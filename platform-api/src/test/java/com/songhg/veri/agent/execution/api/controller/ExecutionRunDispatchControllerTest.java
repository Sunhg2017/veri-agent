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
import java.time.Instant;
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
import static org.mockito.ArgumentMatchers.any;
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
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.rawBaseUrlStored").value(false))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.rawOutputStored").value(false))
                .andExpect(content().string(not(containsString("https://api.example.test/billing"))))
                .andExpect(content().string(not(containsString("Bearer must-redact"))));

        verify(runnerPort).run(any(ApiAutomationRunnerPort.RunnerRunRequest.class));
    }

    private UUID createSingleApiPlan(SeededBundle bundle, String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/execution/plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "name", "Release API smoke",
                                "environmentKey", "qa",
                                "status", "READY",
                                "dag", Map.of("nodes", List.of(Map.of(
                                        "key", "api-smoke",
                                        "type", "API_TEST",
                                        "dependencies", List.of(),
                                        "input", Map.of(
                                                "apiAutomationBundleId", bundle.bundleId().toString(),
                                                "caseIds", List.of(bundle.caseIds().getFirst().toString())
                                        ),
                                        "timeoutSeconds", 120,
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
