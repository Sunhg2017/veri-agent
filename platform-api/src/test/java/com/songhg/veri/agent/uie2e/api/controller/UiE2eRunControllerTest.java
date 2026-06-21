package com.songhg.veri.agent.uie2e.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.management.application.port.ManagementStoreParams;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentConnectivityTargetRow;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentRef;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentRuntimeRef;
import com.songhg.veri.agent.uie2e.application.port.UiE2eArtifactStorage;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.domain.UiE2eArtifactManifest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.ui-e2e.runner-enabled=false",
        "veri-agent.ui-e2e.runner-mode=managed",
        "veri-agent.ui-e2e.max-scenes-per-run=5",
        "veri-agent.ui-e2e.allowlist-base-urls[0]=https://portal.example.test"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class UiE2eRunControllerTest {

    private static final String PROJECT_ID = "8e78afe7-f4e6-441c-a2ba-0d1041e3844f";
    private static final UUID PROJECT_UUID = UUID.fromString(PROJECT_ID);
    private static final UUID ENVIRONMENT_UUID = UUID.fromString("2d5eb6af-0fda-4ec2-bc7f-c30f69c307e4");
    private static final String ENVIRONMENT_KEY = "staging";
    private static final String SECRET_REF = "secret://wp8/accounts/admin-01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UiE2eRepository uiE2eRepository;

    @Autowired
    private UiE2eArtifactStorage artifactStorage;

    @MockitoBean
    private ManagementStore managementStore;

    @Test
    void protectsRunApisByPermissionAndProjectScope() throws Exception {
        String readOnlyToken = userAccessToken(List.of("Developer@PROJECT:" + PROJECT_ID));

        mockMvc.perform(get("/api/v1/ui-e2e/runs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readOnlyToken)
                        .param("projectId", PROJECT_ID))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ui-e2e/runs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readOnlyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "sceneId", UUID.randomUUID(),
                                "bundleId", UUID.randomUUID(),
                                "baseUrlRef", "env:" + ENVIRONMENT_KEY,
                                "accountLeaseRef", UUID.randomUUID()
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void createsListsCancelsAndExportsRunWithoutSecretEcho() throws Exception {
        stubManagementStore("https://portal.example.test");
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:" + PROJECT_ID));
        String readNoExportToken = userAccessToken(List.of("Tester@PROJECT:" + PROJECT_ID));
        String auditorToken = userAccessToken(List.of("Auditor@PROJECT:" + PROJECT_ID));
        UUID sceneId = createApprovedScene(ownerToken);
        UUID bundleId = createApprovedBundle(ownerToken, sceneId);
        UUID leaseRef = createLease(ownerToken);
        Map<String, Object> createPayload = new LinkedHashMap<>();
        createPayload.put("projectId", PROJECT_ID);
        createPayload.put("sceneId", sceneId.toString());
        createPayload.put("bundleId", bundleId.toString());
        createPayload.put("environmentId", ENVIRONMENT_KEY);
        createPayload.put("baseUrlRef", "env:" + ENVIRONMENT_KEY);
        createPayload.put("accountLeaseRef", leaseRef.toString());
        createPayload.put("requestKey", "run-request-ctrl-001");
        createPayload.put("reason", "manual smoke");
        createPayload.put("browsers", List.of("CHROMIUM", "FIREFOX"));
        createPayload.put("visualRegressionEnabled", true);
        createPayload.put("visualMismatchThreshold", 0.02D);

        MvcResult created = mockMvc.perform(post("/api/v1/ui-e2e/runs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.runnerMode").value("DISABLED"))
                .andExpect(jsonPath("$.data.failureCode").value("UI_E2E_RUNNER_DISABLED"))
                .andExpect(jsonPath("$.data.accountSummary.accountLeaseRef").value(leaseRef.toString()))
                .andExpect(jsonPath("$.data.accountSummary.secretRefDigest").isString())
                .andExpect(jsonPath("$.data.executionSummary.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.executionSummary.browserTypes[0]").value("CHROMIUM"))
                .andExpect(jsonPath("$.data.executionSummary.browserTypes[1]").value("FIREFOX"))
                .andExpect(jsonPath("$.data.executionSummary.browserCount").value(2))
                .andExpect(jsonPath("$.data.executionSummary.parallelExecutionEnabled").value(true))
                .andExpect(jsonPath("$.data.executionSummary.visualRegressionEnabled").value(true))
                .andExpect(jsonPath("$.data.executionSummary.visualMismatchThreshold").value(0.02D))
                .andExpect(jsonPath("$.data.executionSummary.runnerDefaultDisabled").value(true))
                .andExpect(jsonPath("$.data.executionSummary.stepStatusCounts.RUNNING").value(0))
                .andExpect(jsonPath("$.data.stepResults.length()").value(0))
                .andExpect(jsonPath("$.data.artifacts.length()").value(0))
                .andExpect(content().string(not(containsString(SECRET_REF))))
                .andExpect(content().string(not(containsString("secret://"))))
                .andReturn();
        UUID runId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(post("/api/v1/ui-e2e/runs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId.toString()))
                .andExpect(jsonPath("$.data.idempotentReplay").value(true));

        mockMvc.perform(get("/api/v1/ui-e2e/runs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .param("projectId", PROJECT_ID)
                        .param("keyword", "run-request-ctrl-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(runId.toString()))
                .andExpect(jsonPath("$.data.items[0].status").value("BLOCKED"));

        mockMvc.perform(get("/api/v1/ui-e2e/runs/{id}", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId.toString()))
                .andExpect(jsonPath("$.data.sceneCode").isString())
                .andExpect(jsonPath("$.data.bundleStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.stepResults.length()").value(0))
                .andExpect(jsonPath("$.data.artifacts.length()").value(0));

        mockMvc.perform(post("/api/v1/ui-e2e/runs/{id}/cancel", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "cancel blocked run"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"));

        mockMvc.perform(get("/api/v1/ui-e2e/runs/{id}/export", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readNoExportToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/ui-e2e/runs/{id}/export", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schemaVersion").value("wp7-run-export-v1"))
                .andExpect(jsonPath("$.data.run.id").value(runId.toString()))
                .andExpect(jsonPath("$.data.redactionPolicy.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.redactionPolicy.artifactDownloadReady").value(false))
                .andExpect(content().string(not(containsString(SECRET_REF))))
                .andExpect(content().string(not(containsString("secret://"))));
    }

    @Test
    void rejectsRunWhenResolvedBaseUrlFallsOutsideAllowlist() throws Exception {
        stubManagementStore("https://admin.example.test");
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:" + PROJECT_ID));
        UUID sceneId = createApprovedScene(ownerToken);
        UUID bundleId = createApprovedBundle(ownerToken, sceneId);
        UUID leaseRef = createLease(ownerToken);

        mockMvc.perform(post("/api/v1/ui-e2e/runs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "sceneId", sceneId.toString(),
                                "bundleId", bundleId.toString(),
                                "environmentId", ENVIRONMENT_KEY,
                                "baseUrlRef", "env:" + ENVIRONMENT_KEY,
                                "accountLeaseRef", leaseRef.toString(),
                                "requestKey", "run-request-ctrl-002"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value("UI_E2E_BASE_URL_NOT_ALLOWED"));
    }

    @Test
    void downloadsStoredArtifactForAuthorizedUser() throws Exception {
        stubManagementStore("https://portal.example.test");
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:" + PROJECT_ID));
        String auditorToken = userAccessToken(List.of("Auditor@PROJECT:" + PROJECT_ID));
        UUID sceneId = createApprovedScene(ownerToken);
        UUID bundleId = createApprovedBundle(ownerToken, sceneId);
        UUID leaseRef = createLease(ownerToken);

        MvcResult created = mockMvc.perform(post("/api/v1/ui-e2e/runs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "sceneId", sceneId.toString(),
                                "bundleId", bundleId.toString(),
                                "environmentId", ENVIRONMENT_KEY,
                                "baseUrlRef", "env:" + ENVIRONMENT_KEY,
                                "accountLeaseRef", leaseRef.toString(),
                                "requestKey", "run-request-ctrl-download-001"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID runId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
        UUID artifactId = UUID.randomUUID();
        Path tempFile = Files.createTempFile("wp7-artifact-", ".log");
        try {
            Files.writeString(tempFile, "wp7 artifact body", StandardCharsets.UTF_8);
            UiE2eArtifactStorage.StoredArtifact stored = artifactStorage.store(runId, artifactId, "LOG", tempFile);
            uiE2eRepository.replaceArtifacts(runId, List.of(new UiE2eArtifactManifest(
                    artifactId,
                    runId,
                    "LOG",
                    stored.storageRef(),
                    "4f0df0d66f54d6e52d4f3a9e4d4b3a3d0f0a7c6a5d4e3f2c1b0a998877665544",
                    stored.sizeBytes(),
                    "{\"aggregateOnly\":true,\"rawArtifactStored\":true,\"rawArtifactDownloadReady\":true}",
                    "CAPTURED",
                    "tester",
                    "tester",
                    Instant.now(),
                    Instant.now()
            )));
        } finally {
            Files.deleteIfExists(tempFile);
        }

        mockMvc.perform(get("/api/v1/ui-e2e/runs/{id}/artifacts/{artifactId}/download", runId, artifactId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(artifactId.toString())))
                .andExpect(content().string("wp7 artifact body"));
    }

    @Test
    void createsBatchRunsWithPerSceneOutcomes() throws Exception {
        stubManagementStore("https://portal.example.test");
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:" + PROJECT_ID));
        UUID sceneIdOne = createApprovedScene(ownerToken);
        UUID sceneIdTwo = createApprovedScene(ownerToken);
        createApprovedBundle(ownerToken, sceneIdOne);
        createApprovedBundle(ownerToken, sceneIdTwo);
        UUID leaseRef = createLease(ownerToken);

        mockMvc.perform(post("/api/v1/ui-e2e/runs/batch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "sceneIds", List.of(sceneIdOne.toString(), sceneIdTwo.toString(), UUID.randomUUID().toString()),
                                "environmentId", ENVIRONMENT_KEY,
                                "baseUrlRef", "env:" + ENVIRONMENT_KEY,
                                "accountLeaseRef", leaseRef.toString(),
                                "requestKeyPrefix", "run-batch-ctrl-001",
                                "reason", "batch smoke",
                                "browsers", List.of("CHROMIUM", "FIREFOX"),
                                "visualRegressionEnabled", true,
                                "visualMismatchThreshold", 0.02D
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(PROJECT_ID))
                .andExpect(jsonPath("$.data.requestedCount").value(3))
                .andExpect(jsonPath("$.data.createdCount").value(2))
                .andExpect(jsonPath("$.data.replayedCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].outcome").value("CREATED"))
                .andExpect(jsonPath("$.data.items[1].outcome").value("CREATED"))
                .andExpect(jsonPath("$.data.items[2].outcome").value("FAILED"))
                .andExpect(jsonPath("$.data.items[2].errorCode").value("UI_E2E_SCENE_NOT_FOUND"));
    }

    @Test
    void backfillsRunSummaryWithoutMutatingMatchingRun() throws Exception {
        stubManagementStore("https://portal.example.test");
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:" + PROJECT_ID));
        UUID sceneId = createApprovedScene(ownerToken);
        UUID bundleId = createApprovedBundle(ownerToken, sceneId);
        UUID leaseRef = createLease(ownerToken);

        MvcResult created = mockMvc.perform(post("/api/v1/ui-e2e/runs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "sceneId", sceneId.toString(),
                                "bundleId", bundleId.toString(),
                                "environmentId", ENVIRONMENT_KEY,
                                "baseUrlRef", "env:" + ENVIRONMENT_KEY,
                                "accountLeaseRef", leaseRef.toString(),
                                "requestKey", "run-request-backfill-001"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID runId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(post("/api/v1/ui-e2e/runs/backfill")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "runIds", List.of(runId.toString())
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(PROJECT_ID))
                .andExpect(jsonPath("$.data.requestedCount").value(1))
                .andExpect(jsonPath("$.data.updatedCount").value(1))
                .andExpect(jsonPath("$.data.unchangedCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.items[0].runId").value(runId.toString()))
                .andExpect(jsonPath("$.data.items[0].updated").value(true))
                .andExpect(jsonPath("$.data.items[0].stepResultCount").value(0))
                .andExpect(jsonPath("$.data.items[0].artifactCount").value(0));
    }

    private UUID createApprovedScene(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/ui-e2e/scenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "applicationId", "app-alpha",
                                "environmentId", ENVIRONMENT_KEY,
                                "code", "portal-run-scene-" + UUID.randomUUID().toString().substring(0, 8),
                                "name", "后台管理员登录并进入首页",
                                "status", "APPROVED",
                                "riskLevel", "HIGH",
                                "tags", List.of("login", "smoke"),
                                "steps", List.of(Map.of(
                                        "stepType", "LOGIN",
                                        "actionSummary", Map.of("submitAction", "click"),
                                        "locatorStrategy", Map.of("preferred", "testId"),
                                        "assertionSummary", Map.of("successSignal", "url contains /dashboard"),
                                        "waitPolicy", Map.of("timeoutSeconds", 5)
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private UUID createApprovedBundle(String token, UUID sceneId) throws Exception {
        MvcResult bundleCreated = mockMvc.perform(post("/api/v1/ui-e2e/bundles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sceneId", sceneId.toString()))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID bundleId = UUID.fromString(JsonPath.read(bundleCreated.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(post("/api/v1/ui-e2e/bundles/{id}/submit-review", bundleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "ready"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ui-e2e/bundles/{id}/approve", bundleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "approved"))))
                .andExpect(status().isOk());
        return bundleId;
    }

    private UUID createLease(String token) throws Exception {
        MvcResult poolCreated = mockMvc.perform(post("/api/v1/test-data/account-pools")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(poolRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        UUID poolId = UUID.fromString(JsonPath.read(poolCreated.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(post("/api/v1/test-data/account-pools/{id}/accounts", poolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountRequest())))
                .andExpect(status().isOk());

        MvcResult leaseCreated = mockMvc.perform(post("/api/v1/test-data/leases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "applicationId", "app-alpha",
                                "environmentId", ENVIRONMENT_KEY,
                                "poolId", poolId.toString(),
                                "roleTags", List.of("ADMIN"),
                                "holderType", "EXECUTION_RUN",
                                "holderRef", "wp7-run-" + UUID.randomUUID(),
                                "ttlSeconds", 60,
                                "requestKey", "lease-" + UUID.randomUUID()
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(JsonPath.read(leaseCreated.getResponse().getContentAsString(), "$.data.id"));
    }

    private Map<String, Object> poolRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectId", PROJECT_ID);
        request.put("applicationId", "app-alpha");
        request.put("environmentId", ENVIRONMENT_KEY);
        request.put("code", "pool-run-" + UUID.randomUUID().toString().substring(0, 8));
        request.put("name", "Run pool");
        request.put("status", "READY");
        request.put("defaultTtlSeconds", 60);
        return request;
    }

    private Map<String, Object> accountRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("accountKey", "admin-" + UUID.randomUUID().toString().substring(0, 8));
        request.put("displayName", "Admin 01");
        request.put("status", "AVAILABLE");
        request.put("roleTags", List.of("ADMIN"));
        request.put("scopeSummary", Map.of("applicationId", "app-alpha"));
        request.put("secretRef", SECRET_REF);
        return request;
    }

    private void stubManagementStore(String webUrl) {
        when(managementStore.findEnvironmentRuntimeRef(any(ManagementStoreParams.class)))
                .thenReturn(new EnvironmentRuntimeRef(
                        ENVIRONMENT_UUID,
                        PROJECT_UUID,
                        ENVIRONMENT_KEY,
                        "Staging",
                        "https://api.example.test/runtime",
                        "ENABLED"
                ));
        when(managementStore.findEnvironmentRef(any(ManagementStoreParams.class)))
                .thenReturn(new EnvironmentRef(
                        ENVIRONMENT_UUID,
                        "Staging",
                        "ENABLED",
                        PROJECT_UUID,
                        "Project Alpha"
                ));
        when(managementStore.findEnvironmentConnectivityTarget(any(ManagementStoreParams.class)))
                .thenReturn(new EnvironmentConnectivityTargetRow(
                        ENVIRONMENT_UUID,
                        "Staging",
                        "ENABLED",
                        webUrl,
                        "https://api.example.test/runtime",
                        "{}"
                ));
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "wp7-run-user-" + UUID.randomUUID(),
                "WP7 Run User",
                "wp7-run-user@example.test",
                "{noop}password",
                false,
                1,
                roles
        )).accessToken();
    }
}
