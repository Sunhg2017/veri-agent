package com.songhg.veri.agent.execution.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.execution.max-concurrent-runs-per-project=3",
        "veri-agent.execution.max-concurrent-nodes-per-run=5",
        "veri-agent.xxl-job.enabled=false"
})
@AutoConfigureMockMvc
class ExecutionHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesControlPlaneHealthWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/execution/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.service").value("execution"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.schedulerEnabled").value(false))
                .andExpect(jsonPath("$.data.webhookEnabled").value(false))
                .andExpect(jsonPath("$.data.cronEnabled").value(false))
                .andExpect(jsonPath("$.data.schedulerIntervalMs").value(5000))
                .andExpect(jsonPath("$.data.schedulerInitialDelayMs").value(30000))
                .andExpect(jsonPath("$.data.schedulerWorkerId").value("wp9-managed-worker"))
                .andExpect(jsonPath("$.data.schedulerTickBatchSize").value(4))
                .andExpect(jsonPath("$.data.maxConcurrentRunsPerProject").value(3))
                .andExpect(jsonPath("$.data.maxConcurrentNodesPerRun").value(5))
                .andExpect(jsonPath("$.data.schedulerLeaderLockEnabled").value(true))
                .andExpect(jsonPath("$.data.schedulerLeaderLockName").value("wp9:execution:scheduler:leader"))
                .andExpect(jsonPath("$.data.schedulerLeaderLockWaitMs").value(0))
                .andExpect(jsonPath("$.data.schedulerLeaderLockLeaseMs").value(120000))
                .andExpect(jsonPath("$.data.schedulerLeaderLockProvider").value("LOCAL_JVM"))
                .andExpect(jsonPath("$.data.schedulerLeaderLockDistributed").value(false))
                .andExpect(jsonPath("$.data.schedulerLeaderLockReady").value(true))
                .andExpect(jsonPath("$.data.policy.controlPlaneReady").value(true))
                .andExpect(jsonPath("$.data.policy.planCrudReady").value(true))
                .andExpect(jsonPath("$.data.policy.dagDryRunReady").value(true))
                .andExpect(jsonPath("$.data.policy.manualTriggerReady").value(true))
                .andExpect(jsonPath("$.data.policy.cancelRetryReady").value(true))
                .andExpect(jsonPath("$.data.policy.queueClaimReady").value(true))
                .andExpect(jsonPath("$.data.policy.heartbeatRecoveryReady").value(true))
                .andExpect(jsonPath("$.data.policy.stateAggregationReady").value(true))
                .andExpect(jsonPath("$.data.policy.wp6DispatchReady").value(true))
                .andExpect(jsonPath("$.data.policy.wp6DispatchViaApplicationService").value(true))
                .andExpect(jsonPath("$.data.policy.wp7DispatchReady").value(true))
                .andExpect(jsonPath("$.data.policy.wp7DispatchViaApplicationService").value(true))
                .andExpect(jsonPath("$.data.policy.wp7AsyncFollowUpReady").value(true))
                .andExpect(jsonPath("$.data.policy.schedulerLoopReady").value(true))
                .andExpect(jsonPath("$.data.policy.schedulerManagedByXxlJob").value(false))
                .andExpect(jsonPath("$.data.policy.schedulerRuntimeReady").value(false))
                .andExpect(jsonPath("$.data.policy.schedulerUsesQueueClaim").value(true))
                .andExpect(jsonPath("$.data.policy.schedulerLeaderLockEnabled").value(true))
                .andExpect(jsonPath("$.data.policy.schedulerLeaderLockProvider").value("LOCAL_JVM"))
                .andExpect(jsonPath("$.data.policy.schedulerLeaderLockDistributed").value(false))
                .andExpect(jsonPath("$.data.policy.schedulerLeaderLockReady").value(true))
                .andExpect(jsonPath("$.data.policy.schedulerMultiActiveReady").value(false))
                .andExpect(jsonPath("$.data.policy.triggerControlPlaneReady").value(true))
                .andExpect(jsonPath("$.data.policy.webhookSignatureReady").value(true))
                .andExpect(jsonPath("$.data.policy.triggerEventIdempotencyReady").value(true))
                .andExpect(jsonPath("$.data.policy.cronMetadataReady").value(true))
                .andExpect(jsonPath("$.data.policy.cronScannerReady").value(true))
                .andExpect(jsonPath("$.data.policy.cronRuntimeReady").value(false))
                .andExpect(jsonPath("$.data.policy.webhookDefaultDisabled").value(true))
                .andExpect(jsonPath("$.data.policy.secretPlaintextStored").value(false))
                .andExpect(jsonPath("$.data.policy.directRunnerAdapterCallAllowed").value(false))
                .andExpect(jsonPath("$.data.policy.supportedNodeTypes[0]").value("API_TEST"))
                .andExpect(jsonPath("$.data.policy.p0ExecutableNodeTypes[1]").value("UI_TEST"));
    }
}
