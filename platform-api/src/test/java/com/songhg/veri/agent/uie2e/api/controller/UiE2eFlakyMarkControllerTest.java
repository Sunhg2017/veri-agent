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

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.ui-e2e.runner-enabled=false",
        "veri-agent.ui-e2e.runner-mode=managed",
        "veri-agent.ui-e2e.allowlist-base-urls[0]=https://portal.example.test"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class UiE2eFlakyMarkControllerTest {

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

    @MockitoBean
    private ManagementStore managementStore;

    @Test
    void protectsFlakyApisByPermissionAndListsMarks() throws Exception {
        stubManagementStore("https://portal.example.test");
        String readOnlyToken = userAccessToken(List.of("Developer@PROJECT:" + PROJECT_ID));
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:" + PROJECT_ID));
        UUID sceneId = createApprovedScene(ownerToken);
        UUID bundleId = createApprovedBundle(ownerToken, sceneId);
        UUID leaseRef = createLease(ownerToken);
        UUID runId = createBlockedRun(ownerToken, sceneId, bundleId, leaseRef);

        MvcResult sceneLevel = mockMvc.perform(post("/api/v1/ui-e2e/flaky-marks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "sceneId", sceneId.toString(),
                                "status", "FLAKY_CANDIDATE",
                                "reasonCode", "locator-drift"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        String sceneLevelId = JsonPath.read(sceneLevel.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/ui-e2e/flaky-marks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readOnlyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "sceneId", sceneId.toString(),
                                "runId", runId.toString(),
                                "status", "FLAKY_CANDIDATE"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/ui-e2e/flaky-marks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "sceneId", sceneId.toString(),
                                "runId", runId.toString(),
                                "status", "CONFIRMED_FLAKY",
                                "reasonCode", "locator-drift",
                                "reasonSummary", "locator changes after deploy"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.projectId").value(PROJECT_ID))
                .andExpect(jsonPath("$.data.id").value(sceneLevelId))
                .andExpect(jsonPath("$.data.sceneId").value(sceneId.toString()))
                .andExpect(jsonPath("$.data.runId").value(runId.toString()))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED_FLAKY"))
                .andExpect(jsonPath("$.data.reasonCode").value("LOCATOR_DRIFT"))
                .andExpect(jsonPath("$.data.runStatus").value("BLOCKED"));

        mockMvc.perform(get("/api/v1/ui-e2e/flaky-marks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readOnlyToken)
                        .param("projectId", PROJECT_ID)
                        .param("keyword", "locator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].runId").value(runId.toString()))
                .andExpect(jsonPath("$.data.items[0].sceneCode").isString())
                .andExpect(jsonPath("$.data.items[0].status").value("CONFIRMED_FLAKY"));

        mockMvc.perform(get("/api/v1/ui-e2e/flaky-marks/{id}", sceneLevelId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readOnlyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(sceneLevelId))
                .andExpect(jsonPath("$.data.runId").value(runId.toString()))
                .andExpect(jsonPath("$.data.sceneRiskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.linkedRunCount").value(1))
                .andExpect(jsonPath("$.data.latestFailureBucket").value("RUNNER"))
                .andExpect(jsonPath("$.data.updatedBy").isString())
                .andExpect(jsonPath("$.data.reasonSummary").value("locator changes after deploy"));
    }

    private UUID createApprovedScene(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/ui-e2e/scenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "applicationId", "app-alpha",
                                "environmentId", ENVIRONMENT_KEY,
                                "code", "portal-flaky-scene-" + UUID.randomUUID().toString().substring(0, 8),
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
                                "holderRef", "wp7-flaky-run-" + UUID.randomUUID(),
                                "ttlSeconds", 60,
                                "requestKey", "lease-" + UUID.randomUUID()
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(JsonPath.read(leaseCreated.getResponse().getContentAsString(), "$.data.id"));
    }

    private UUID createBlockedRun(String token, UUID sceneId, UUID bundleId, UUID leaseRef) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/ui-e2e/runs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "sceneId", sceneId.toString(),
                                "bundleId", bundleId.toString(),
                                "environmentId", ENVIRONMENT_KEY,
                                "baseUrlRef", "env:" + ENVIRONMENT_KEY,
                                "accountLeaseRef", leaseRef.toString(),
                                "requestKey", "run-request-flaky-001"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private Map<String, Object> poolRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectId", PROJECT_ID);
        request.put("applicationId", "app-alpha");
        request.put("environmentId", ENVIRONMENT_KEY);
        request.put("code", "pool-flaky-" + UUID.randomUUID().toString().substring(0, 8));
        request.put("name", "Flaky pool");
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
                "wp7-flaky-user-" + UUID.randomUUID(),
                "WP7 Flaky User",
                "wp7-flaky-user@example.test",
                "{noop}password",
                false,
                1,
                roles
        )).accessToken();
    }
}
