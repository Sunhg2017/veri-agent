package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.execution.application.view.ExecutionHealthResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import com.songhg.veri.agent.scheduling.config.XxlJobProperties;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ExecutionHealthService {

    private final ExecutionProperties properties;
    private final XxlJobProperties xxlJobProperties;

    public ExecutionHealthService(ExecutionProperties properties, XxlJobProperties xxlJobProperties) {
        this.properties = properties;
        this.xxlJobProperties = xxlJobProperties;
    }

    /**
     * Publishes control-plane readiness and safety boundaries without exposing runtime queue payloads.
     */
    public ExecutionHealthResponse health() {
        boolean schedulerManagedByXxlJob = xxlJobProperties != null && xxlJobProperties.enabled();
        boolean schedulerRuntimeReady = properties.schedulerEnabled() && schedulerManagedByXxlJob;
        boolean cronRuntimeReady = properties.cronEnabled() && schedulerRuntimeReady;
        return new ExecutionHealthResponse(
                "execution",
                "UP",
                properties.schedulerEnabled(),
                properties.webhookEnabled(),
                properties.cronEnabled(),
                properties.effectiveSchedulerIntervalMs(),
                properties.effectiveSchedulerInitialDelayMs(),
                properties.effectiveSchedulerWorkerId(),
                properties.effectiveSchedulerTickBatchSize(),
                properties.effectiveMaxConcurrentRunsPerProject(),
                properties.effectiveMaxConcurrentNodesPerRun(),
                properties.effectiveNodeHeartbeatTimeoutSeconds(),
                properties.effectiveDefaultRunTimeoutSeconds(),
                properties.effectiveRecoveryBatchSize(),
                Map.ofEntries(
                        Map.entry("controlPlaneReady", true),
                        Map.entry("planCrudReady", true),
                        Map.entry("dagDryRunReady", true),
                        Map.entry("manualTriggerReady", true),
                        Map.entry("cancelRetryReady", true),
                        Map.entry("queueClaimReady", true),
                        Map.entry("heartbeatRecoveryReady", true),
                        Map.entry("stateAggregationReady", true),
                        Map.entry("wp6DispatchReady", true),
                        Map.entry("wp6DispatchViaApplicationService", true),
                        Map.entry("wp7DispatchReady", true),
                        Map.entry("wp7DispatchViaApplicationService", true),
                        Map.entry("wp7AsyncFollowUpReady", true),
                        Map.entry("wp8AccountLeaseAdapterReady", true),
                        Map.entry("accountLeaseAutoAcquireReleaseReady", true),
                        Map.entry("accountLeaseStoredFields",
                                List.of("accountLeaseRef", "accountPoolRef", "status", "expiresAt", "releasedAt")),
                        Map.entry("schedulerLoopReady", true),
                        Map.entry("schedulerManagedByXxlJob", schedulerManagedByXxlJob),
                        Map.entry("schedulerRuntimeReady", schedulerRuntimeReady),
                        Map.entry("schedulerUsesQueueClaim", true),
                        Map.entry("triggerControlPlaneReady", true),
                        Map.entry("webhookSignatureReady", true),
                        Map.entry("triggerEventIdempotencyReady", true),
                        Map.entry("cronMetadataReady", true),
                        Map.entry("cronScannerReady", true),
                        Map.entry("cronRuntimeReady", cronRuntimeReady),
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
                        Map.entry("p0ExecutableNodeTypes", List.of("API_TEST", "UI_TEST", "REPORT_HANDOFF"))
                )
        );
    }
}
