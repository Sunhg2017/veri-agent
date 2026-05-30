package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignGenerationOrchestrationPolicyResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * Centralizes the WP5 generation orchestration visibility boundary.
 *
 * <p>The policy exposes only aggregate queue/runtime counters and fixed capability flags. It deliberately keeps task
 * identifiers, event identifiers, queue payloads, idempotency keys and timeout error bodies out of API, prompt and
 * report snapshots so operations can spot lag/timeout risk without turning observability into a sensitive index.
 */
public final class TestDesignGenerationOrchestrationPolicy {

    public static final String POLICY_VERSION = "wp5-generation-orchestration-policy-v1";
    public static final String ASYNC_ORCHESTRATION_MODE = "ASYNC_EVENT_CONDITIONAL_CLAIM";
    public static final String SYNC_ORCHESTRATION_MODE = "SYNC_INLINE_GENERATION";
    private static final int MAX_RECOVERY_BATCH_SIZE = 100;

    private TestDesignGenerationOrchestrationPolicy() {
    }

    public static TestDesignGenerationOrchestrationPolicyResponse response(
            TestDesignProperties properties,
            RuntimeSnapshot runtime
    ) {
        return response(properties, runtime, null);
    }

    public static TestDesignGenerationOrchestrationPolicyResponse taskResponse(
            TestDesignProperties properties,
            TestDesignTask task
    ) {
        return response(properties, RuntimeSnapshot.empty(), task);
    }

    public static TestDesignGenerationOrchestrationPolicyResponse taskResponse(
            TestDesignProperties properties,
            TestDesignTask task,
            RuntimeSnapshot runtime
    ) {
        return response(properties, runtime, task);
    }

    public static TestDesignGenerationOrchestrationPolicyResponse response(TestDesignProperties properties) {
        return response(properties, RuntimeSnapshot.empty(), null);
    }

    public static Map<String, Object> snapshot(TestDesignProperties properties) {
        TestDesignGenerationOrchestrationPolicyResponse response = response(
                properties,
                RuntimeSnapshot.empty(),
                null
        );
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyVersion", response.policyVersion());
        snapshot.put("orchestrationMode", response.orchestrationMode());
        snapshot.put("asyncGenerationEnabled", response.asyncGenerationEnabled());
        snapshot.put("conditionalRunClaimSupported", response.conditionalRunClaimSupported());
        snapshot.put("idempotentCreateReplaySupported", response.idempotentCreateReplaySupported());
        snapshot.put("duplicateEventReplaySafe", response.duplicateEventReplaySafe());
        snapshot.put("eventRecoveryEnabled", response.eventRecoveryEnabled());
        snapshot.put("queuedEventReplaySupported", response.queuedEventReplaySupported());
        snapshot.put("runningTimeoutRecoveryEnabled", response.runningTimeoutRecoveryEnabled());
        snapshot.put("explicitRetryRequiredAfterTimeout", response.explicitRetryRequiredAfterTimeout());
        snapshot.put("manualTaskRetrySupported", response.manualTaskRetrySupported());
        snapshot.put("manualQueuedEventReplayReady", response.manualQueuedEventReplayReady());
        snapshot.put("queueLagMetricReady", response.queueLagMetricReady());
        snapshot.put("timeoutAlertReady", response.timeoutAlertReady());
        snapshot.put("multiInstanceLoadTestEvidenceReady", response.multiInstanceLoadTestEvidenceReady());
        snapshot.put("eventPayloadExported", response.eventPayloadExported());
        snapshot.put("eventIdentifierListExported", response.eventIdentifierListExported());
        snapshot.put("queueMessageBodyExported", response.queueMessageBodyExported());
        snapshot.put("recoveryDetailRowsExported", response.recoveryDetailRowsExported());
        snapshot.put("effectiveRecoveryBatchSize", response.effectiveRecoveryBatchSize());
        snapshot.put("runningTimeoutSeconds", response.runningTimeoutSeconds());
        snapshot.put("queueLagWarningSeconds", response.queueLagWarningSeconds());
        snapshot.put("queuedStatusSignal", response.queuedStatusSignal());
        snapshot.put("runningStatusSignal", response.runningStatusSignal());
        snapshot.put("timeoutFailureSignal", response.timeoutFailureSignal());
        snapshot.put("aggregateOnly", response.aggregateOnly());
        return snapshot;
    }

    static int recoveryBatchSize(TestDesignProperties properties) {
        int configured = properties.eventRecoveryBatchSize();
        return Math.max(1, Math.min(MAX_RECOVERY_BATCH_SIZE,
                configured <= 0 ? MAX_RECOVERY_BATCH_SIZE : configured));
    }

    static long queueLagWarningSeconds(TestDesignProperties properties) {
        return Math.max(0L, properties.eventRecoveryQueueLagWarningSeconds());
    }

    static long runningTimeoutSeconds(TestDesignProperties properties) {
        return Math.max(0L, properties.eventRecoveryRunningTimeoutSeconds());
    }

    public static long ageSeconds(Instant now, Instant timestamp) {
        if (now == null || timestamp == null || timestamp.isAfter(now)) {
            return 0L;
        }
        return Math.max(0L, Duration.between(timestamp, now).getSeconds());
    }

    private static TestDesignGenerationOrchestrationPolicyResponse response(
            TestDesignProperties properties,
            RuntimeSnapshot runtime,
            TestDesignTask task
    ) {
        boolean asyncEnabled = properties.asyncGenerationEnabled();
        boolean recoveryEnabled = properties.eventRecoveryEnabled();
        long timeoutSeconds = runningTimeoutSeconds(properties);
        boolean timeoutRecoveryEnabled = recoveryEnabled && timeoutSeconds > 0L;
        long queueLagSeconds = queueLagWarningSeconds(properties);
        RuntimeSnapshot safeRuntime = runtime == null ? RuntimeSnapshot.empty() : runtime;
        return new TestDesignGenerationOrchestrationPolicyResponse(
                POLICY_VERSION,
                asyncEnabled ? ASYNC_ORCHESTRATION_MODE : SYNC_ORCHESTRATION_MODE,
                asyncEnabled,
                true,
                true,
                true,
                recoveryEnabled,
                asyncEnabled && recoveryEnabled,
                timeoutRecoveryEnabled,
                true,
                true,
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                recoveryBatchSize(properties),
                timeoutSeconds,
                queueLagSeconds,
                Math.max(0L, safeRuntime.queuedTaskCount()),
                Math.max(0L, safeRuntime.runningTaskCount()),
                Math.max(0L, safeRuntime.oldestQueuedAgeSeconds()),
                Math.max(0L, safeRuntime.staleRunningTaskCount()),
                queueLagSeconds > 0L && safeRuntime.oldestQueuedAgeSeconds() >= queueLagSeconds,
                timeoutRecoveryEnabled && safeRuntime.staleRunningTaskCount() > 0L,
                statusSignal(task, "QUEUED"),
                statusSignal(task, "RUNNING"),
                timeoutFailureSignal(task),
                true
        );
    }

    private static long statusSignal(TestDesignTask task, String status) {
        return task != null && status.equals(task.status()) ? 1L : 0L;
    }

    private static long timeoutFailureSignal(TestDesignTask task) {
        if (task == null || !"FAILED".equals(task.status()) || !StringUtils.hasText(task.errorMessage())) {
            return 0L;
        }
        String message = task.errorMessage();
        return message.contains("运行超时") || message.toLowerCase(java.util.Locale.ROOT).contains("timeout") ? 1L : 0L;
    }

    public record RuntimeSnapshot(
            long queuedTaskCount,
            long runningTaskCount,
            long oldestQueuedAgeSeconds,
            long staleRunningTaskCount
    ) {
        public static RuntimeSnapshot empty() {
            return new RuntimeSnapshot(0L, 0L, 0L, 0L);
        }
    }
}
