package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import java.time.Instant;
import org.springframework.util.StringUtils;

final class TestDesignTaskReportGenerationOrchestrationPolicyRows {

    private static final int MAX_RECOVERY_BATCH_SIZE = 100;
    private static final String POLICY_VERSION = "wp5-generation-orchestration-policy-v1";
    private static final String ASYNC_ORCHESTRATION_MODE = "ASYNC_EVENT_CONDITIONAL_CLAIM";
    private static final String SYNC_ORCHESTRATION_MODE = "SYNC_INLINE_GENERATION";

    private TestDesignTaskReportGenerationOrchestrationPolicyRows() {
    }

    /**
     * Appends generation orchestration readiness without exporting queue messages or recovery details.
     *
     * <p>The rows document the current WP5 task orchestration contract: async events are consumed through a conditional
     * RUNNING claim, queued events can be re-emitted by recovery scanning, and stale RUNNING tasks are failed for
     * explicit retry. Operational gaps such as queue-lag alerts, manual event replay and multi-instance evidence remain
     * visible as fixed flags, while event IDs, queue payloads, task lists and timeout error text stay out of the CSV.
     */
    static void appendRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            TestDesignProperties properties
    ) {
        boolean asyncEnabled = properties.asyncGenerationEnabled();
        boolean recoveryEnabled = properties.eventRecoveryEnabled();
        boolean timeoutRecoveryEnabled = recoveryEnabled && properties.eventRecoveryRunningTimeoutSeconds() > 0L;
        appendMetadataRow(csv, task, generatedAt, "policyVersion", POLICY_VERSION, null);
        appendMetadataRow(csv, task, generatedAt, "orchestrationMode",
                asyncEnabled ? ASYNC_ORCHESTRATION_MODE : SYNC_ORCHESTRATION_MODE, asyncEnabled ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "asyncGenerationEnabled", asyncEnabled,
                asyncEnabled ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "conditionalRunClaimSupported", true, "success");
        appendMetadataRow(csv, task, generatedAt, "idempotentCreateReplaySupported", true, "success");
        appendMetadataRow(csv, task, generatedAt, "duplicateEventReplaySafe", true, "success");
        appendMetadataRow(csv, task, generatedAt, "eventRecoveryEnabled", recoveryEnabled,
                recoveryEnabled ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "queuedEventReplaySupported", asyncEnabled && recoveryEnabled,
                asyncEnabled && recoveryEnabled ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "runningTimeoutRecoveryEnabled", timeoutRecoveryEnabled,
                timeoutRecoveryEnabled ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "explicitRetryRequiredAfterTimeout", true, "success");
        appendMetadataRow(csv, task, generatedAt, "manualTaskRetrySupported", true, "success");
        appendMetadataRow(csv, task, generatedAt, "manualQueuedEventReplayReady", false, "warning");
        appendMetadataRow(csv, task, generatedAt, "queueLagMetricReady", false, "warning");
        appendMetadataRow(csv, task, generatedAt, "timeoutAlertReady", false, "warning");
        appendMetadataRow(csv, task, generatedAt, "multiInstanceLoadTestEvidenceReady", false, "warning");
        appendMetadataRow(csv, task, generatedAt, "eventPayloadExported", false, null);
        appendMetadataRow(csv, task, generatedAt, "eventIdentifierListExported", false, null);
        appendMetadataRow(csv, task, generatedAt, "queueMessageBodyExported", false, null);
        appendMetadataRow(csv, task, generatedAt, "recoveryDetailRowsExported", false, null);
        appendMetricRow(csv, task, generatedAt, "effectiveRecoveryBatchSize", recoveryBatchSize(properties), "info");
        appendMetricRow(csv, task, generatedAt, "runningTimeoutSeconds",
                Math.max(0L, properties.eventRecoveryRunningTimeoutSeconds()), timeoutRecoveryEnabled ? "info" : "neutral");
        appendMetricRow(csv, task, generatedAt, "queuedStatusSignal", statusSignal(task, "QUEUED"), "info");
        appendMetricRow(csv, task, generatedAt, "runningStatusSignal", statusSignal(task, "RUNNING"), "info");
        appendMetricRow(csv, task, generatedAt, "timeoutFailureSignal", timeoutFailureSignal(task), "warning");
        appendMetadataRow(csv, task, generatedAt, "aggregateOnly", true, "success");
    }

    private static int recoveryBatchSize(TestDesignProperties properties) {
        int configured = properties.eventRecoveryBatchSize();
        return Math.max(1, Math.min(MAX_RECOVERY_BATCH_SIZE,
                configured <= 0 ? MAX_RECOVERY_BATCH_SIZE : configured));
    }

    private static long statusSignal(TestDesignTaskResponse task, String status) {
        return status.equals(task.status()) ? 1L : 0L;
    }

    private static long timeoutFailureSignal(TestDesignTaskResponse task) {
        if (!"FAILED".equals(task.status()) || !StringUtils.hasText(task.errorMessage())) {
            return 0L;
        }
        String message = task.errorMessage();
        return message.contains("运行超时") || message.toLowerCase().contains("timeout") ? 1L : 0L;
    }

    private static void appendMetadataRow(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            String metric,
            Object value,
            String tone
    ) {
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "generationOrchestrationPolicy", metric, null, value, null, tone, "fullTask", null);
    }

    private static void appendMetricRow(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            String label,
            long value,
            String nonZeroTone
    ) {
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "summary", "generationOrchestrationPolicy", "metric", label, value, null,
                value > 0L ? nonZeroTone : "neutral", "fullTask", null);
    }
}
