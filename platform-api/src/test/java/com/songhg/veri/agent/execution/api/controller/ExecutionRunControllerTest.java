package com.songhg.veri.agent.execution.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.execution.node-heartbeat-timeout-seconds=1"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExecutionRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiAutomationRepository apiAutomationRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Test
    void createsManualRunAndReplaysRequestKeyIdempotently() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        UUID planId = createPlan(bundleId, token, "READY");

        MvcResult created = mockMvc.perform(post("/api/v1/execution/plans/{id}/runs", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestKey", "release-2026-06-13-smoke",
                                "reason", "release gate",
                                "variables", Map.of("buildId", "20260613.1")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.planId").value(planId.toString()))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.triggerType").value("MANUAL"))
                .andExpect(jsonPath("$.data.requestKey").value("release-2026-06-13-smoke"))
                .andExpect(jsonPath("$.data.idempotentReplay").value(false))
                .andExpect(jsonPath("$.data.resultSummary.nodeCount").value(2))
                .andExpect(jsonPath("$.data.resultSummary.runnerDispatched").value(false))
                .andExpect(jsonPath("$.data.nodes[0].status").value("QUEUED"))
                .andExpect(jsonPath("$.data.nodes[0].runnerType").value("WP6_API"))
                .andExpect(jsonPath("$.data.nodes[1].status").value("PENDING"))
                .andReturn();

        UUID runId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(post("/api/v1/execution/plans/{id}/runs", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestKey", "release-2026-06-13-smoke",
                                "reason", "client retry"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId.toString()))
                .andExpect(jsonPath("$.data.idempotentReplay").value(true))
                .andExpect(jsonPath("$.data.nodes.length()").value(2));

        mockMvc.perform(get("/api/v1/execution/runs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("projectId", "project-alpha")
                        .param("planId", planId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(runId.toString()))
                .andExpect(jsonPath("$.data.items[0].nodeCount").value(2));

        mockMvc.perform(get("/api/v1/execution/runs/{id}", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId.toString()))
                .andExpect(jsonPath("$.data.idempotentReplay").value(false))
                .andExpect(jsonPath("$.data.nodes.length()").value(2));
    }

    @Test
    void rejectsManualRunWhenPlanIsNotReady() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        UUID planId = createPlan(bundleId, token, "DRAFT");

        mockMvc.perform(post("/api/v1/execution/plans/{id}/runs", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("requestKey", "draft-plan"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value("EXECUTION_PLAN_NOT_READY"));
    }

    @Test
    void cancelsQueuedRunIdempotentlyWithoutRunnerDispatch() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        UUID planId = createPlan(bundleId, token, "READY");
        UUID runId = triggerRun(planId, token, "cancel-smoke");

        mockMvc.perform(post("/api/v1/execution/runs/{id}/cancel", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId.toString()))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.errorCode").value("EXECUTION_RUN_CANCELED"))
                .andExpect(jsonPath("$.data.resultSummary.canceled").value(true))
                .andExpect(jsonPath("$.data.resultSummary.runnerCancelAttempted").value(false))
                .andExpect(jsonPath("$.data.nodes[0].status").value("CANCELED"))
                .andExpect(jsonPath("$.data.nodes[0].errorCode").value("EXECUTION_RUN_CANCELED"))
                .andExpect(jsonPath("$.data.nodes[1].status").value("CANCELED"));

        mockMvc.perform(post("/api/v1/execution/runs/{id}/cancel", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId.toString()))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.nodes.length()").value(2));
    }

    @Test
    void retriesFailedRunOnceAndRejectsNonRetryableRun() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        UUID planId = createPlan(bundleId, token, "READY");
        UUID runId = triggerRun(planId, token, "retry-smoke");

        mockMvc.perform(post("/api/v1/execution/runs/{id}/retry", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value("EXECUTION_RUN_NOT_RETRYABLE"));

        markFirstNodeFailed(runId);

        mockMvc.perform(post("/api/v1/execution/runs/{id}/retry", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId.toString()))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.triggerType").value("RETRY"))
                .andExpect(jsonPath("$.data.attempt").value(2))
                .andExpect(jsonPath("$.data.resultSummary.retryInFlight").value(true))
                .andExpect(jsonPath("$.data.resultSummary.retryNodeCount").value(1))
                .andExpect(jsonPath("$.data.resultSummary.runnerDispatched").value(false))
                .andExpect(jsonPath("$.data.nodes.length()").value(3))
                .andExpect(jsonPath("$.data.nodes[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.nodes[0].attempt").value(1))
                .andExpect(jsonPath("$.data.nodes[1].status").value("QUEUED"))
                .andExpect(jsonPath("$.data.nodes[1].attempt").value(2))
                .andExpect(jsonPath("$.data.nodes[1].resultSummary.previousAttempt").value(1));

        mockMvc.perform(post("/api/v1/execution/runs/{id}/retry", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.triggerType").value("RETRY"))
                .andExpect(jsonPath("$.data.nodes.length()").value(3));
    }

    @Test
    void claimsQueuedNodeAndCompletesItWithDependencyAggregation() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        String adminToken = userAccessToken(List.of("SuperAdmin"));
        UUID planId = createPlan(bundleId, ownerToken, "READY");
        UUID runId = triggerRun(planId, ownerToken, "claim-success-smoke");

        MvcResult claimed = mockMvc.perform(post("/api/v1/execution/internal/queue/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("workerId", "worker-a"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.runId").value(runId.toString()))
                .andExpect(jsonPath("$.data.nodeKey").value("api-smoke"))
                .andExpect(jsonPath("$.data.workerId").value("worker-a"))
                .andExpect(jsonPath("$.data.claimToken", startsWith("wp9_claim_")))
                .andReturn();
        UUID nodeRunId = UUID.fromString(JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.nodeRunId"));
        String claimToken = JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.claimToken");

        mockMvc.perform(post("/api/v1/execution/internal/queue/node-runs/{id}/complete", nodeRunId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "claimToken", claimToken,
                                "status", "SUCCEEDED",
                                "resultSummary", Map.of(
                                        "caseCount", 7,
                                        "durationMs", 1200,
                                        "stdout", "raw output must not be stored",
                                        "message", "completed without token=wp9_secret_123456789"
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.resultSummary.succeededNodeCount").value(1))
                .andExpect(jsonPath("$.data.resultSummary.queuedNodeCount").value(1))
                .andExpect(jsonPath("$.data.nodes[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.caseCount").value(7))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.rawOutputStored").value(false))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.stdout").doesNotExist())
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.message").value("completed without [REDACTED]"))
                .andExpect(jsonPath("$.data.nodes[1].status").value("QUEUED"))
                .andExpect(jsonPath("$.data.nodes[1].resultSummary.dependenciesSatisfied").value(true));
    }

    @Test
    void completingClaimedFailureBlocksFailFastDependentsAndAllowsNoopClaim() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        String adminToken = userAccessToken(List.of("SuperAdmin"));
        UUID planId = createPlan(bundleId, ownerToken, "READY");
        UUID runId = triggerRun(planId, ownerToken, "claim-failure-smoke");

        MvcResult claimed = mockMvc.perform(post("/api/v1/execution/internal/queue/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("workerId", "worker-b"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID nodeRunId = UUID.fromString(JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.nodeRunId"));
        String claimToken = JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.claimToken");

        mockMvc.perform(post("/api/v1/execution/internal/queue/node-runs/{id}/complete", nodeRunId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "claimToken", claimToken,
                                "status", "FAILED",
                                "errorCode", "EXECUTION_NODE_DISPATCH_FAILED",
                                "errorSummary", "sanitized failure with bearer wp9secrettoken",
                                "resultSummary", Map.of(
                                        "failureCategory", "ASSERTION",
                                        "requestBody", "{\"password\":\"plain\"}",
                                        "nested", Map.of("token", "plain", "safe", "apiKey=sk_live_123456789")
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId.toString()))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("EXECUTION_RUN_FAILED"))
                .andExpect(jsonPath("$.data.resultSummary.failedNodeCount").value(1))
                .andExpect(jsonPath("$.data.resultSummary.blockedNodeCount").value(1))
                .andExpect(jsonPath("$.data.nodes[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.nodes[0].errorCode").value("EXECUTION_NODE_DISPATCH_FAILED"))
                .andExpect(jsonPath("$.data.nodes[0].errorSummary").value("sanitized failure with [REDACTED]"))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.requestBody").doesNotExist())
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.nested.token").doesNotExist())
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.nested.safe").value("[REDACTED]"))
                .andExpect(jsonPath("$.data.nodes[1].status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.nodes[1].errorCode").value("EXECUTION_DEPENDENCY_BLOCKED"));

        mockMvc.perform(post("/api/v1/execution/internal/queue/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("workerId", "worker-b"))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void heartbeatsClaimAndRequeuesExpiredClaimForAnotherWorker() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        String adminToken = userAccessToken(List.of("SuperAdmin"));
        UUID planId = createPlan(bundleId, ownerToken, "READY");
        UUID runId = triggerRun(planId, ownerToken, "heartbeat-recovery-smoke");

        MvcResult claimed = mockMvc.perform(post("/api/v1/execution/internal/queue/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("workerId", "worker-heartbeat"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID nodeRunId = UUID.fromString(JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.nodeRunId"));
        String claimToken = JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.claimToken");

        mockMvc.perform(post("/api/v1/execution/internal/queue/claims/heartbeat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("claimToken", claimToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(runId.toString()))
                .andExpect(jsonPath("$.data.nodeRunId").value(nodeRunId.toString()))
                .andExpect(jsonPath("$.data.workerId").value("worker-heartbeat"))
                .andExpect(jsonPath("$.data.claimToken").value(claimToken));

        Thread.sleep(1200);

        mockMvc.perform(post("/api/v1/execution/internal/queue/recover-expired")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiredClaimCount").value(1))
                .andExpect(jsonPath("$.data.requeuedNodeCount").value(1))
                .andExpect(jsonPath("$.data.timedOutNodeCount").value(0))
                .andExpect(jsonPath("$.data.aggregatedRunCount").value(1));

        mockMvc.perform(post("/api/v1/execution/internal/queue/node-runs/{id}/complete", nodeRunId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "claimToken", claimToken,
                                "status", "SUCCEEDED"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("EXECUTION_QUEUE_CLAIM_NOT_ACTIVE"));

        mockMvc.perform(post("/api/v1/execution/internal/queue/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("workerId", "worker-after-recovery"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.runId").value(runId.toString()))
                .andExpect(jsonPath("$.data.nodeRunId").value(nodeRunId.toString()))
                .andExpect(jsonPath("$.data.workerId").value("worker-after-recovery"));
    }

    @Test
    void recoveryTimesOutRunningNodeWhenPlanTimeoutElapsed() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        String adminToken = userAccessToken(List.of("SuperAdmin"));
        UUID planId = createPlan(bundleId, ownerToken, "READY", 1);
        UUID runId = triggerRun(planId, ownerToken, "timeout-recovery-smoke");

        mockMvc.perform(post("/api/v1/execution/internal/queue/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("workerId", "worker-timeout"))
                .andExpect(status().isCreated());

        Thread.sleep(1200);

        mockMvc.perform(post("/api/v1/execution/internal/queue/recover-expired")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiredClaimCount").value(1))
                .andExpect(jsonPath("$.data.requeuedNodeCount").value(0))
                .andExpect(jsonPath("$.data.timedOutNodeCount").value(1))
                .andExpect(jsonPath("$.data.aggregatedRunCount").value(1));

        mockMvc.perform(get("/api/v1/execution/runs/{id}", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TIMEOUT"))
                .andExpect(jsonPath("$.data.errorCode").value("EXECUTION_RUN_TIMEOUT"))
                .andExpect(jsonPath("$.data.resultSummary.timeoutNodeCount").value(1))
                .andExpect(jsonPath("$.data.nodes[0].status").value("TIMEOUT"))
                .andExpect(jsonPath("$.data.nodes[0].resultSummary.recoveryAction").value("TIMED_OUT"))
                .andExpect(jsonPath("$.data.nodes[1].status").value("BLOCKED"));
    }

    private UUID createPlan(UUID bundleId, String token, String status) throws Exception {
        return createPlan(bundleId, token, status, 180);
    }

    private UUID createPlan(UUID bundleId, String token, String status, int apiTimeoutSeconds) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/execution/plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(planRequest(bundleId, status, apiTimeoutSeconds))))
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

    private void markFirstNodeFailed(UUID runId) {
        Instant now = Instant.now();
        ExecutionRun run = executionRepository.run(runId).orElseThrow();
        ExecutionNodeRun firstNode = executionRepository.nodeRuns(runId).get(0);
        executionRepository.updateNodeRuns(List.of(new ExecutionNodeRun(
                firstNode.id(),
                firstNode.runId(),
                firstNode.planNodeId(),
                "FAILED",
                firstNode.attempt(),
                firstNode.runnerType(),
                firstNode.externalRunId(),
                "EXECUTION_NODE_DISPATCH_FAILED",
                "simulated sanitized node failure",
                "{\"simulatedFailure\":true,\"runnerDispatched\":false}",
                firstNode.heartbeatAt(),
                firstNode.queuedAt(),
                firstNode.startedAt(),
                now,
                firstNode.createdAt(),
                now
        )));
        executionRepository.updateRun(new ExecutionRun(
                run.id(),
                run.planId(),
                run.projectId(),
                "FAILED",
                run.triggerType(),
                run.requestKey(),
                run.sourceEventId(),
                run.attempt(),
                run.traceId(),
                "{\"nodeCount\":2,\"failedNodeCount\":1,\"runnerDispatched\":false}",
                "EXECUTION_NODE_DISPATCH_FAILED",
                "simulated sanitized run failure",
                run.createdBy(),
                run.startedAt(),
                now,
                run.createdAt(),
                now
        ));
    }

    private UUID approvedBundle(String projectId) {
        UUID bundleId = UUID.randomUUID();
        Instant now = Instant.now();
        apiAutomationRepository.insertScriptBundle(new ApiAutomationScriptBundle(
                bundleId,
                projectId,
                UUID.randomUUID(),
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
        return bundleId;
    }

    private Map<String, Object> planRequest(UUID bundleId, String status) {
        return planRequest(bundleId, status, 180);
    }

    private Map<String, Object> planRequest(UUID bundleId, String status, int apiTimeoutSeconds) {
        return Map.of(
                "projectId", "project-alpha",
                "name", "Release smoke",
                "environmentKey", "staging",
                "status", status,
                "dag", Map.of("nodes", List.of(
                        Map.of(
                                "key", "api-smoke",
                                "type", "API_TEST",
                                "dependencies", List.of(),
                                "input", Map.of("apiAutomationBundleId", bundleId.toString()),
                                "timeoutSeconds", apiTimeoutSeconds,
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
}
