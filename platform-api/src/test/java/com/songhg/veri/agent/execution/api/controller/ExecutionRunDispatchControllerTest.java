package com.songhg.veri.agent.execution.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationGenerationTask;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentRuntimeRef;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.testdata.application.view.TestDataCrossWpAccountSummary;
import com.songhg.veri.agent.testdata.application.view.TestDataExecutionAccountLeaseResponse;
import com.songhg.veri.agent.uie2e.application.UiE2eRunService;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eRunCommand;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunDetailResponse;
import java.time.Instant;
import java.util.Collections;
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
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.api-automation.runner-enabled=true",
        "veri-agent.api-automation.runner-allowed-base-url-patterns=api.example.test"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExecutionRunDispatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiAutomationRepository apiAutomationRepository;

    @MockitoBean
    private ApiAutomationRunnerPort runnerPort;

    @MockitoBean
    private ManagementStore managementStore;

    @MockitoBean
    private UiE2eRunService uiE2eRunService;

    @MockitoBean
    private TestDataCrossWpReferenceService testDataCrossWpReferenceService;

    @Test
    void dispatchesClaimedApiTestNodeThroughWp6AndAggregatesSuccess() throws Exception {
        when(runnerPort.validateBundle(any(ApiAutomationScriptBundle.class)))
                .thenReturn(new ApiAutomationRunnerPort.RunnerValidation(true, null, null));
        when(runnerPort.run(any(ApiAutomationRunnerPort.RunnerRunRequest.class))).thenAnswer(invocation -> {
            ApiAutomationRunnerPort.RunnerRunRequest request = invocation.getArgument(0);
            return new ApiAutomationRunnerPort.RunnerRunResult(
                    "PASSED",
                    "MANAGED",
                    null,
                    null,
                    request.cases().stream()
                            .map(automationCase -> new ApiAutomationRunnerPort.RunnerCaseResult(
                                    automationCase.id(),
                                    "PASSED",
                                    37,
                                    "{\"statusCode\":200,\"authorization\":\"Bearer must-redact\",\"rawRequestResponseStored\":true}",
                                    null,
                                    null
                            ))
                            .toList()
            );
        });
        SeededBundle seededBundle = approvedBundle("project-alpha");
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        String adminToken = userAccessToken(List.of("SuperAdmin"));
        UUID planId = createSingleApiPlan(seededBundle, ownerToken);
        UUID runId = triggerRun(planId, ownerToken, "dispatch-success-smoke");

        MvcResult claimed = mockMvc.perform(post("/api/v1/execution/internal/queue/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("workerId", "worker-dispatch-success"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID nodeRunId = UUID.fromString(JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.nodeRunId"));
        String claimToken = JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.claimToken");

        mockMvc.perform(post("/api/v1/execution/internal/queue/node-runs/{id}/dispatch", nodeRunId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "claimToken", claimToken,
                                "baseUrl", "https://api.example.test/billing",
                                "environmentId", "qa"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId.toString()))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.resultSummary.runnerDispatched").value(true))
                .andExpect(jsonPath("$.data.resultSummary.succeededNodeCount").value(1))
                .andExpect(jsonPath("$.data.nodes[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.nodes[0].externalRunId").exists())
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.runnerDispatched").value(true))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.wp6Status").value("PASSED"))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.wp6RunnerMode").value("MANAGED"))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.wp6CaseCount").value(1))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.wp6ResultCounts.PASSED").value(1))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.wp6BaseUrlHost").value("api.example.test"))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.baseUrlSource").value("REQUEST_BASE_URL"))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.rawBaseUrlStored").value(false))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.rawOutputStored").value(false))
                .andExpect(content().string(not(containsString("https://api.example.test/billing"))))
                .andExpect(content().string(not(containsString("Bearer must-redact"))));

        verify(runnerPort).run(any(ApiAutomationRunnerPort.RunnerRunRequest.class));
    }

    @Test
    void resolvesPlanBaseUrlRefAndKeepsRuntimeSecretRefsInsideWp6Boundary() throws Exception {
        String projectId = UUID.randomUUID().toString();
        when(managementStore.findEnvironmentRuntimeRef(argThat(params -> "staging".equals(params.get("keyword"))
                && UUID.fromString(projectId).equals(params.get("projectId")))))
                .thenReturn(new EnvironmentRuntimeRef(
                        UUID.randomUUID(),
                        UUID.fromString(projectId),
                        "staging",
                        "Staging",
                        "https://api.example.test/runtime",
                        "ENABLED"
                ));
        when(runnerPort.validateBundle(any(ApiAutomationScriptBundle.class)))
                .thenReturn(new ApiAutomationRunnerPort.RunnerValidation(true, null, null));
        SeededBundle seededBundle = approvedBundle(projectId);
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:" + projectId));
        String adminToken = userAccessToken(List.of("SuperAdmin"));
        UUID planId = createSingleApiPlan(projectId, seededBundle, ownerToken, Map.of(
                "apiAutomationBundleId", seededBundle.bundleId().toString(),
                "baseUrlRef", "env:staging",
                "caseIds", List.of(seededBundle.caseIds().getFirst().toString()),
                "runtimeSecretRefs", List.of("secret://wp6/runtime-token")
        ));

        mockMvc.perform(post("/api/v1/execution/plans/{id}/runs", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("requestKey", "dispatch-ref-smoke"))))
                .andExpect(status().isCreated())
                .andExpect(content().string(not(containsString("secret://wp6/runtime-token"))));

        MvcResult claimed = mockMvc.perform(post("/api/v1/execution/internal/queue/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("workerId", "worker-dispatch-ref"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID nodeRunId = UUID.fromString(JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.nodeRunId"));
        String claimToken = JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.claimToken");

        mockMvc.perform(post("/api/v1/execution/internal/queue/node-runs/{id}/dispatch", nodeRunId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("claimToken", claimToken))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("SECRET_PROVIDER_ERROR"))
                .andExpect(content().string(containsString("sha256:")))
                .andExpect(content().string(not(containsString("https://api.example.test/runtime"))))
                .andExpect(content().string(not(containsString("secret://wp6/runtime-token"))));
    }

    @Test
    void resolvesPlanBaseUrlRefWhenNoRuntimeSecretsAreRequired() throws Exception {
        String projectId = UUID.randomUUID().toString();
        when(managementStore.findEnvironmentRuntimeRef(argThat(params -> "staging".equals(params.get("keyword"))
                && UUID.fromString(projectId).equals(params.get("projectId")))))
                .thenReturn(new EnvironmentRuntimeRef(
                        UUID.randomUUID(),
                        UUID.fromString(projectId),
                        "staging",
                        "Staging",
                        "https://api.example.test/runtime",
                        "ENABLED"
                ));
        when(runnerPort.validateBundle(any(ApiAutomationScriptBundle.class)))
                .thenReturn(new ApiAutomationRunnerPort.RunnerValidation(true, null, null));
        when(runnerPort.run(any(ApiAutomationRunnerPort.RunnerRunRequest.class))).thenAnswer(invocation -> {
            ApiAutomationRunnerPort.RunnerRunRequest request = invocation.getArgument(0);
            return new ApiAutomationRunnerPort.RunnerRunResult(
                    "PASSED",
                    "MANAGED",
                    null,
                    null,
                    request.cases().stream()
                            .map(automationCase -> new ApiAutomationRunnerPort.RunnerCaseResult(
                                    automationCase.id(),
                                    "PASSED",
                                    24,
                                    "{}",
                                    null,
                                    null
                            ))
                            .toList()
            );
        });
        SeededBundle seededBundle = approvedBundle(projectId);
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:" + projectId));
        String adminToken = userAccessToken(List.of("SuperAdmin"));
        UUID planId = createSingleApiPlan(projectId, seededBundle, ownerToken, Map.of(
                "apiAutomationBundleId", seededBundle.bundleId().toString(),
                "baseUrlRef", "env:staging",
                "caseIds", List.of(seededBundle.caseIds().getFirst().toString())
        ));
        triggerRun(planId, ownerToken, "dispatch-ref-success-" + UUID.randomUUID());

        MvcResult claimed = mockMvc.perform(post("/api/v1/execution/internal/queue/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("workerId", "worker-dispatch-ref-success"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID nodeRunId = UUID.fromString(JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.nodeRunId"));
        String claimToken = JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.claimToken");

        mockMvc.perform(post("/api/v1/execution/internal/queue/node-runs/{id}/dispatch", nodeRunId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("claimToken", claimToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.baseUrlSource").value("PLAN_BASE_URL_REF"))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.baseUrlRefDigest").value(startsWith("sha256:")))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.runtimeSecretRefCount").value(0))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.secretRefsStored").value(false))
                .andExpect(content().string(not(containsString("https://api.example.test/runtime"))));

        verify(runnerPort).run(argThat(request -> "https://api.example.test/runtime".equals(request.baseUrl())
                && request.secretRefDigests().isEmpty()));
    }

    @Test
    void mapsWp6FailedAndTimeoutResultsToWp9TerminalStatuses() throws Exception {
        SeededBundle failedBundle = approvedBundle("project-alpha");
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        String adminToken = userAccessToken(List.of("SuperAdmin"));
        assertDispatchStatus(failedBundle, ownerToken, adminToken, "FAILED", "ASSERTION_FAILED", "FAILED");

        SeededBundle timeoutBundle = approvedBundle("project-alpha");
        assertDispatchStatus(timeoutBundle, ownerToken, adminToken, "TIMEOUT", "RUNNER_TIMEOUT", "TIMEOUT");
    }

    @Test
    void dispatchesClaimedUiTestNodeThroughWp7AndHidesBaseUrlRefPlaintext() throws Exception {
        String projectId = "project-alpha";
        UUID sceneId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UUID accountLeaseRef = UUID.randomUUID();
        UUID wp7RunId = UUID.randomUUID();
        when(testDataCrossWpReferenceService.acquireExecutionRunLease(any()))
                .thenReturn(new TestDataExecutionAccountLeaseResponse(
                        accountLeaseRef,
                        projectId,
                        "ACTIVE",
                        Instant.now().plusSeconds(600),
                        null,
                        new TestDataCrossWpAccountSummary(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                projectId,
                                "admin-01",
                                "Admin 01",
                                "LEASED",
                                List.of("ADMIN"),
                                Map.of("applicationId", "portal-web", "environmentId", "staging"),
                                "sha256:lease-secret",
                                "HEALTHY"
                        ),
                        Map.of("wp9StoresAccountLeaseRefOnly", true)
                ));
        when(uiE2eRunService.createRun(any(CreateUiE2eRunCommand.class))).thenAnswer(invocation -> {
            CreateUiE2eRunCommand command = invocation.getArgument(0);
            return new UiE2eRunDetailResponse(
                    wp7RunId,
                    projectId,
                    sceneId,
                    "portal-login-smoke",
                    "Portal login smoke",
                    "APPROVED",
                    bundleId,
                    "APPROVED",
                    "BLOCKED",
                    command.requestKey(),
                    "MANAGED",
                    "RUNNER_DISABLED",
                    "runner disabled without secret leakage",
                    "trc_wp7_dispatch",
                    Map.of(
                            "accountLeaseRef", accountLeaseRef.toString(),
                            "secretRefDigest", "sha256:lease-secret"
                    ),
                    Map.of(
                            "baseUrlDigest", "sha256:portal-staging",
                            "stepStatusCounts", Map.of("BLOCKED", 1),
                            "failureBucketCounts", Map.of("RUNNER_FAILURE", 1),
                            "artifactTypes", List.of("SCREENSHOT")
                    ),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    null,
                    Instant.now().minusSeconds(5),
                    Instant.now(),
                    Instant.now().minusSeconds(10),
                    Instant.now(),
                    false
            );
        });
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:" + projectId));
        String adminToken = userAccessToken(List.of("SuperAdmin"));
        UUID planId = createSingleUiPlan(projectId, ownerToken, sceneId, bundleId);
        UUID runId = triggerRun(planId, ownerToken, "dispatch-ui-smoke");

        MvcResult claimed = mockMvc.perform(post("/api/v1/execution/internal/queue/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("workerId", "worker-dispatch-ui"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID nodeRunId = UUID.fromString(JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.nodeRunId"));
        String claimToken = JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.claimToken");

        mockMvc.perform(post("/api/v1/execution/internal/queue/node-runs/{id}/dispatch", nodeRunId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "claimToken", claimToken
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId.toString()))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.resultSummary.runnerDispatched").value(true))
                .andExpect(jsonPath("$.data.nodes[0].status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.nodes[0].externalRunId").value(wp7RunId.toString()))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.wp7DispatchReady").value(true))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.wp7RunId").value(wp7RunId.toString()))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.wp7RunnerMode").value("MANAGED"))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.wp7Status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.wp7TerminalSnapshot").value(true))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.wp7AsyncFollowUpRequired").value(false))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.baseUrlRefDigest").value(startsWith("sha256:")))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.accountLeaseRef").value(accountLeaseRef.toString()))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.secretRefPlaintextStored").value(false))
                .andExpect(content().string(not(containsString("env:portal-staging"))))
                .andExpect(content().string(not(containsString("secret://"))));

        verify(uiE2eRunService).createRun(argThat(command ->
                projectId.equals(command.projectId())
                        && sceneId.equals(command.sceneId())
                        && bundleId.equals(command.bundleId())
                        && "staging".equals(command.environmentId())
                        && "env:portal-staging".equals(command.baseUrlRef())
                        && accountLeaseRef.equals(command.accountLeaseRef())
                        && command.requestKey().startsWith("wp9-ui:")));
    }

    private void assertDispatchStatus(
            SeededBundle bundle,
            String ownerToken,
            String adminToken,
            String wp6Status,
            String errorCode,
            String expectedWp9Status
    ) throws Exception {
        when(runnerPort.validateBundle(any(ApiAutomationScriptBundle.class)))
                .thenReturn(new ApiAutomationRunnerPort.RunnerValidation(true, null, null));
        when(runnerPort.run(any(ApiAutomationRunnerPort.RunnerRunRequest.class))).thenReturn(new ApiAutomationRunnerPort.RunnerRunResult(
                wp6Status,
                "MANAGED",
                errorCode,
                wp6Status + " without sensitive data",
                List.of(new ApiAutomationRunnerPort.RunnerCaseResult(
                        bundle.caseIds().getFirst(),
                        wp6Status,
                        88,
                        "{\"safe\":true}",
                        errorCode,
                        wp6Status + " without sensitive data"
                ))
        ));
        UUID planId = createSingleApiPlan(bundle, ownerToken);
        UUID runId = triggerRun(planId, ownerToken, "dispatch-" + wp6Status.toLowerCase() + "-" + UUID.randomUUID());

        MvcResult claimed = mockMvc.perform(post("/api/v1/execution/internal/queue/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("workerId", "worker-dispatch-" + wp6Status.toLowerCase()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID nodeRunId = UUID.fromString(JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.nodeRunId"));
        String claimToken = JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.claimToken");

        mockMvc.perform(post("/api/v1/execution/internal/queue/node-runs/{id}/dispatch", nodeRunId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "claimToken", claimToken,
                                "baseUrl", "https://api.example.test/status-" + wp6Status.toLowerCase()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId.toString()))
                .andExpect(jsonPath("$.data.status").value(expectedWp9Status))
                .andExpect(jsonPath("$.data.nodes[0].status").value(expectedWp9Status))
                .andExpect(jsonPath("$.data.nodes[0].errorCode").value(errorCode))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.wp6Status").value(wp6Status))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.wp6ResultCounts." + wp6Status).value(1))
                .andExpect(content().string(not(containsString("https://api.example.test/status"))));
    }

    private UUID createSingleApiPlan(SeededBundle bundle, String token) throws Exception {
        return createSingleApiPlan("project-alpha", bundle, token, Map.of(
                "apiAutomationBundleId", bundle.bundleId().toString(),
                "caseIds", List.of(bundle.caseIds().getFirst().toString())
        ));
    }

    private UUID createSingleApiPlan(
            String projectId,
            SeededBundle bundle,
            String token,
            Map<String, Object> input
    ) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/execution/plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", projectId,
                                "name", "Release API smoke",
                                "environmentKey", "qa",
                                "status", "READY",
                                "dag", Map.of("nodes", List.of(Map.of(
                                        "key", "api-smoke",
                                        "type", "API_TEST",
                                        "dependencies", List.of(),
                                        "input", input,
                                        "timeoutSeconds", 120,
                                        "failurePolicy", "FAIL_FAST",
                                        "retryPolicy", Map.of("maxAttempts", 1)
                                )))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private UUID createSingleUiPlan(
            String projectId,
            String token,
            UUID sceneId,
            UUID bundleId
    ) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/execution/plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", projectId,
                                "name", "Release UI smoke",
                                "environmentKey", "staging",
                                "status", "READY",
                                "dag", Map.of("nodes", List.of(Map.of(
                                        "key", "ui-smoke",
                                        "type", "UI_TEST",
                                        "dependencies", List.of(),
                                        "input", Map.of(
                                                "sceneId", sceneId.toString(),
                                                "bundleId", bundleId.toString(),
                                                "baseUrlRef", "env:portal-staging",
                                                "environmentId", "staging",
                                                "accountLease", Map.of(
                                                        "accountPoolRef", UUID.randomUUID().toString(),
                                                        "applicationId", "portal-web",
                                                        "environmentId", "staging",
                                                        "roleTags", List.of("ADMIN"),
                                                        "ttlSeconds", 120,
                                                        "requestKey", "ui-smoke-admin"
                                                )
                                        ),
                                        "timeoutSeconds", 180,
                                        "failurePolicy", "FAIL_FAST",
                                        "retryPolicy", Map.of("maxAttempts", 1)
                                )))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private UUID triggerRun(UUID planId, String token, String requestKey) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/execution/plans/{id}/runs", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("requestKey", requestKey))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private SeededBundle approvedBundle(String projectId) {
        UUID specId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        Instant now = Instant.now();
        apiAutomationRepository.insertSpec(new ApiAutomationSpec(
                specId,
                projectId,
                "TEXT",
                null,
                "wp9-dispatch-openapi",
                "2026.06",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                128,
                "{}",
                "{}",
                "PARSED",
                "test-parser",
                2,
                null,
                "tester",
                "tester",
                now,
                now,
                now
        ));
        apiAutomationRepository.insertGenerationTask(new ApiAutomationGenerationTask(
                taskId,
                projectId,
                specId,
                "wp9-dispatch-" + bundleId,
                "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
                "FALLBACK_ONLY",
                "[\"SMOKE\"]",
                "SUCCEEDED",
                "wp6-api-automation-v1",
                "test",
                null,
                true,
                2,
                2,
                "{}",
                null,
                "tester",
                "tester",
                now,
                now
        ));
        ApiAutomationCase firstCase = automationCase(taskId, projectId, specId, "GET /v1/orders", "GET", "/v1/orders", now);
        ApiAutomationCase secondCase = automationCase(taskId, projectId, specId, "POST /v1/orders", "POST", "/v1/orders", now);
        apiAutomationRepository.insertAutomationCase(firstCase);
        apiAutomationRepository.insertAutomationCase(secondCase);
        apiAutomationRepository.insertScriptBundle(new ApiAutomationScriptBundle(
                bundleId,
                projectId,
                taskId,
                "APPROVED",
                "bundle-digest-" + bundleId,
                2,
                "{}",
                "{}",
                "PASSED",
                "{}",
                "approved",
                "submitter",
                "approver",
                now,
                now,
                null,
                "tester",
                "tester",
                now,
                now
        ));
        return new SeededBundle(bundleId, List.of(firstCase.id(), secondCase.id()));
    }

    private ApiAutomationCase automationCase(
            UUID taskId,
            String projectId,
            UUID specId,
            String title,
            String method,
            String path,
            Instant now
    ) {
        return new ApiAutomationCase(
                UUID.randomUUID(),
                taskId,
                projectId,
                specId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                title,
                method,
                path,
                "SMOKE",
                200,
                "{}",
                "{}",
                "FALLBACK",
                "READY",
                now,
                now
        );
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "wp9-run-user-" + UUID.randomUUID(),
                "WP9 Run User",
                "wp9-run-user@example.test",
                "{noop}password",
                false,
                1,
                roles
        )).accessToken();
    }

    private record SeededBundle(UUID bundleId, List<UUID> caseIds) {
    }
}
