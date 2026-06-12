package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.execution.application.view.ExecutionHealthResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ExecutionHealthService {

    private final ExecutionProperties properties;

    public ExecutionHealthService(ExecutionProperties properties) {
        this.properties = properties;
    }

    /**
     * Publishes only control-plane readiness and safety boundaries; runtime queue state is introduced in later WP9 slices.
     */
    public ExecutionHealthResponse health() {
        return new ExecutionHealthResponse(
                "execution",
                "UP",
                properties.schedulerEnabled(),
                properties.webhookEnabled(),
                properties.cronEnabled(),
                properties.effectiveMaxConcurrentRunsPerProject(),
                properties.effectiveMaxConcurrentNodesPerRun(),
                properties.effectiveNodeHeartbeatTimeoutSeconds(),
                properties.effectiveDefaultRunTimeoutSeconds(),
                properties.effectiveRecoveryBatchSize(),
                Map.ofEntries(
                        Map.entry("controlPlaneReady", true),
                        Map.entry("planCrudReady", false),
                        Map.entry("dagDryRunReady", false),
                        Map.entry("manualTriggerReady", false),
                        Map.entry("queueClaimReady", false),
                        Map.entry("wp6DispatchReady", false),
                        Map.entry("webhookDefaultDisabled", !properties.webhookEnabled()),
                        Map.entry("cronDefaultDisabled", !properties.cronEnabled()),
                        Map.entry("schedulerDefaultDisabled", !properties.schedulerEnabled()),
                        Map.entry("rawRunnerOutputStored", false),
                        Map.entry("secretPlaintextStored", false),
                        Map.entry("directRunnerAdapterCallAllowed", false),
                        Map.entry("supportedNodeTypes", List.of(
                                "API_TEST",
                                "UI_TEST",
                                "SETUP",
                                "VERIFY",
                                "CLEANUP",
                                "REPORT_HANDOFF"
                        )),
                        Map.entry("p0ExecutableNodeTypes", List.of("API_TEST", "REPORT_HANDOFF"))
                )
        );
    }
}
