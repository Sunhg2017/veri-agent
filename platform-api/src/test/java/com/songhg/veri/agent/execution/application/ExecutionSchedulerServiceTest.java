package com.songhg.veri.agent.execution.application;

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
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.execution.application.command.CompleteExecutionNodeRunCommand;
import com.songhg.veri.agent.execution.application.view.ExecutionQueueClaimResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionQueueRecoveryResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionSchedulerTickResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentRuntimeRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.execution.scheduler-enabled=true",
        "veri-agent.execution.scheduler-worker-id=wp9-managed-test-worker",
        "veri-agent.execution.scheduler-tick-batch-size=2",
        "veri-agent.execution.scheduler-interval-ms=3600000",
        "veri-agent.execution.scheduler-initial-delay-ms=3600000",
        "veri-agent.api-automation.runner-enabled=true",
        "veri-agent.api-automation.runner-allowed-base-url-patterns=api.example.test"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExecutionSchedulerServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiAutomationRepository apiAutomationRepository;

    @Autowired
    private ExecutionSchedulerService schedulerService;

    @MockitoBean
    private ApiAutomationRunnerPort runnerPort;

    @MockitoBean
    private ManagementStore managementStore;

    @Test
    void runOnceClaimsDispatchesApiTestAndCompletesReportHandoff() throws Exception {
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
                                    31,
                                    "{\"statusCode\":200,\"authorization\":\"Bearer must-redact\"}",
                                    null,
                                    null
                            ))
                            .toList()
            );
        });

        SeededBundle seededBundle = approvedBundle(projectId);
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:" + projectId));
        UUID planId = createApiAndReportPlan(projectId, seededBundle, ownerToken);
        UUID runId = triggerRun(planId, ownerToken, "scheduler-managed-" + UUID.randomUUID());

        ExecutionSchedulerTickResponse tick = schedulerService.runOnce();

        assertThat(tick.schedulerEnabled()).isTrue();
        assertThat(tick.workerId()).isEqualTo("wp9-managed-test-worker");
        assertThat(tick.tickBatchSize()).isEqualTo(2);
        assertThat(tick.recoveredExpiredClaimCount()).isZero();
        assertThat(tick.claimedNodeCount()).isEqualTo(2);
        assertThat(tick.dispatchedNodeCount()).isEqualTo(1);
        assertThat(tick.completedNodeCount()).isEqualTo(1);
        assertThat(tick.failedNodeCount()).isZero();
        assertThat(tick.noop()).isFalse();
        assertThat(tick.traceId()).startsWith("trc_");

        mockMvc.perform(get("/api/v1/execution/runs/{id}", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.resultSummary.succeededNodeCount").value(2))
                .andExpect(jsonPath("$.data.nodes[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.runnerDispatched").value(true))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.baseUrlSource").value("PLAN_BASE_URL_REF"))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.rawBaseUrlStored").value(false))
                .andExpect(jsonPath("$.data.nodes[1].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.nodes[1].resultSummary.schedulerManaged").value(true))
                .andExpect(jsonPath("$.data.nodes[1].resultSummary.reportHandoffReady").value(true))
                .andExpect(jsonPath("$.data.nodes[1].resultSummary.rawReportStored").value(false))
                .andExpect(content().string(not(containsString("https://api.example.test/runtime"))))
                .andExpect(content().string(not(containsString("Bearer must-redact"))));

        verify(runnerPort).run(argThat(request -> "https://api.example.test/runtime".equals(request.baseUrl())));
    }

    @Test
    void runOnceNoopsWhenSchedulerIsDisabled() {
        ExecutionRunService runService = mock(ExecutionRunService.class);
        ExecutionSchedulerService disabledScheduler = new ExecutionSchedulerService(
                runService,
                new ExecutionProperties(
                        false,
                        false,
                        false,
                        5_000,
                        30_000,
                        "disabled-worker",
                        2,
                        2,
                        4,
                        180,
                        1_800,
                        50
                )
        );

        ExecutionSchedulerTickResponse tick = disabledScheduler.runOnce();

        assertThat(tick.schedulerEnabled()).isFalse();
        assertThat(tick.workerId()).isEqualTo("disabled-worker");
        assertThat(tick.claimedNodeCount()).isZero();
        assertThat(tick.noop()).isTrue();
        verifyNoInteractions(runService);
    }

    @Test
    void runOnceUsesEffectiveSchedulerBounds() {
        ExecutionRunService runService = mock(ExecutionRunService.class);
        ExecutionSchedulerService boundedScheduler = new ExecutionSchedulerService(
                runService,
                new ExecutionProperties(
                        false,
                        false,
                        false,
                        -1,
                        0,
                        "  ",
                        999,
                        2,
                        3,
                        180,
                        1_800,
                        50
                )
        );

        ExecutionSchedulerTickResponse tick = boundedScheduler.runOnce();

        assertThat(boundedScheduler.scheduledFixedDelayMillis()).isEqualTo(5_000);
        assertThat(boundedScheduler.scheduledInitialDelayMillis()).isEqualTo(30_000);
        assertThat(tick.workerId()).isEqualTo("wp9-managed-worker");
        assertThat(tick.tickBatchSize()).isEqualTo(3);
        verifyNoInteractions(runService);
    }

    @Test
    void runOnceClosesClaimedNodeWhenDispatchFails() {
        ExecutionRunService runService = mock(ExecutionRunService.class);
        ExecutionSchedulerService scheduler = new ExecutionSchedulerService(
                runService,
                new ExecutionProperties(
                        true,
                        false,
                        false,
                        5_000,
                        30_000,
                        "failure-worker",
                        1,
                        2,
                        4,
                        180,
                        1_800,
                        50
                )
        );
        ExecutionQueueClaimResponse claim = new ExecutionQueueClaimResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "api-smoke",
                "WP6_API",
                "wp9_claim_secret",
                "failure-worker",
                Instant.now(),
                Instant.now(),
                Instant.now().plusSeconds(180)
        );
        when(runService.recoverExpiredQueueClaims()).thenReturn(new ExecutionQueueRecoveryResponse(
                0,
                0,
                0,
                0,
                Instant.now()
        ));
        when(runService.claimNextQueuedNode("failure-worker"))
                .thenReturn(Optional.of(claim))
                .thenReturn(Optional.empty());
        when(runService.dispatchClaimedApiTestNodeRun(any()))
                .thenThrow(new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "baseUrl https://api.example.test/leak secret://wp6/runtime-token token=must-not-store"
                ));

        ExecutionSchedulerTickResponse tick = scheduler.runOnce();

        assertThat(tick.claimedNodeCount()).isEqualTo(1);
        assertThat(tick.failedNodeCount()).isEqualTo(1);
        ArgumentCaptor<CompleteExecutionNodeRunCommand> commandCaptor = ArgumentCaptor.forClass(
                CompleteExecutionNodeRunCommand.class
        );
        verify(runService).completeClaimedNodeRun(commandCaptor.capture());
        CompleteExecutionNodeRunCommand command = commandCaptor.getValue();
        assertThat(command.nodeRunId()).isEqualTo(claim.nodeRunId());
        assertThat(command.claimToken()).isEqualTo(claim.claimToken());
        assertThat(command.status()).isEqualTo("FAILED");
        assertThat(command.errorCode()).isEqualTo("EXECUTION_NODE_DISPATCH_FAILED");
        assertThat(command.errorSummary())
                .doesNotContain("https://api.example.test/leak")
                .doesNotContain("secret://wp6/runtime-token")
                .doesNotContain("must-not-store");
        assertThat(command.resultSummary()).containsEntry("schedulerFailure", true);
    }

    private UUID createApiAndReportPlan(String projectId, SeededBundle bundle, String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/execution/plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", projectId,
                                "name", "Scheduler API smoke",
                                "environmentKey", "staging",
                                "status", "READY",
                                "dag", Map.of("nodes", List.of(
                                        Map.of(
                                                "key", "api-smoke",
                                                "type", "API_TEST",
                                                "dependencies", List.of(),
                                                "input", Map.of(
                                                        "apiAutomationBundleId", bundle.bundleId().toString(),
                                                        "baseUrlRef", "env:staging",
                                                        "caseIds", List.of(bundle.caseIds().getFirst().toString())
                                                ),
                                                "timeoutSeconds", 120,
                                                "failurePolicy", "FAIL_FAST",
                                                "retryPolicy", Map.of("maxAttempts", 1)
                                        ),
                                        Map.of(
                                                "key", "report",
                                                "type", "REPORT_HANDOFF",
                                                "dependencies", List.of("api-smoke"),
                                                "input", Map.of("summaryOnly", true),
                                                "timeoutSeconds", 60,
                                                "failurePolicy", "CONTINUE",
                                                "retryPolicy", Map.of()
                                        )
                                ))
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
                "wp9-scheduler-openapi",
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
                "wp9-scheduler-" + bundleId,
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
                "wp9-scheduler-user-" + UUID.randomUUID(),
                "WP9 Scheduler User",
                "wp9-scheduler-user@example.test",
                "{noop}password",
                false,
                1,
                roles
        )).accessToken();
    }

    private record SeededBundle(UUID bundleId, List<UUID> caseIds) {
    }
}
