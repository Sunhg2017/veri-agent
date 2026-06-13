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
        "veri-agent.execution.max-concurrent-nodes-per-run=5"
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
                .andExpect(jsonPath("$.data.maxConcurrentRunsPerProject").value(3))
                .andExpect(jsonPath("$.data.maxConcurrentNodesPerRun").value(5))
                .andExpect(jsonPath("$.data.policy.controlPlaneReady").value(true))
                .andExpect(jsonPath("$.data.policy.planCrudReady").value(true))
                .andExpect(jsonPath("$.data.policy.dagDryRunReady").value(true))
                .andExpect(jsonPath("$.data.policy.manualTriggerReady").value(true))
                .andExpect(jsonPath("$.data.policy.cancelRetryReady").value(true))
                .andExpect(jsonPath("$.data.policy.wp6DispatchReady").value(false))
                .andExpect(jsonPath("$.data.policy.webhookDefaultDisabled").value(true))
                .andExpect(jsonPath("$.data.policy.secretPlaintextStored").value(false))
                .andExpect(jsonPath("$.data.policy.directRunnerAdapterCallAllowed").value(false))
                .andExpect(jsonPath("$.data.policy.supportedNodeTypes[0]").value("API_TEST"));
    }
}
