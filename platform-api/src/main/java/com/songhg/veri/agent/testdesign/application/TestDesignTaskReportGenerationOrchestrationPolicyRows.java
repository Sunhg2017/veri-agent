package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import java.time.Instant;
import java.util.UUID;

final class TestDesignTaskReportGenerationOrchestrationPolicyRows {

    private TestDesignTaskReportGenerationOrchestrationPolicyRows() {
    }

    /**
     * Appends generation orchestration readiness without exporting queue messages or recovery details.
     *
     * <p>The rows document the current WP5 task orchestration contract: async events are consumed through a conditional
     * RUNNING claim, queued events can be re-emitted by recovery scanning, and stale RUNNING tasks are failed for
     * explicit retry. Operational gaps such as manual event replay and multi-instance evidence remain
     * visible as fixed flags, while event IDs, queue payloads, task lists and timeout error text stay out of the CSV.
     */
    static void appendRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            TestDesignProperties properties
    ) {
        var policy = task.generationOrchestrationPolicy();
        if (policy == null) {
            policy = TestDesignGenerationOrchestrationPolicy.taskResponse(properties, taskDomainSnapshot(task));
        }
        appendMetadataRow(csv, task, generatedAt, "policyVersion", policy.policyVersion(), null);
        appendMetadataRow(csv, task, generatedAt, "orchestrationMode", policy.orchestrationMode(),
                policy.asyncGenerationEnabled() ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "asyncGenerationEnabled", policy.asyncGenerationEnabled(),
                policy.asyncGenerationEnabled() ? "success" : "warning");
        appendBooleanPolicyRow(csv, task, generatedAt, "conditionalRunClaimSupported",
                policy.conditionalRunClaimSupported(), "success", "danger");
        appendBooleanPolicyRow(csv, task, generatedAt, "idempotentCreateReplaySupported",
                policy.idempotentCreateReplaySupported(), "success", "danger");
        appendBooleanPolicyRow(csv, task, generatedAt, "duplicateEventReplaySafe",
                policy.duplicateEventReplaySafe(), "success", "danger");
        appendBooleanPolicyRow(csv, task, generatedAt, "eventRecoveryEnabled",
                policy.eventRecoveryEnabled(), "success", "warning");
        appendBooleanPolicyRow(csv, task, generatedAt, "queuedEventReplaySupported",
                policy.queuedEventReplaySupported(), "success", "warning");
        appendBooleanPolicyRow(csv, task, generatedAt, "runningTimeoutRecoveryEnabled",
                policy.runningTimeoutRecoveryEnabled(), "success", "warning");
        appendBooleanPolicyRow(csv, task, generatedAt, "explicitRetryRequiredAfterTimeout",
                policy.explicitRetryRequiredAfterTimeout(), "success", "danger");
        appendBooleanPolicyRow(csv, task, generatedAt, "manualTaskRetrySupported",
                policy.manualTaskRetrySupported(), "success", "danger");
        appendBooleanPolicyRow(csv, task, generatedAt, "manualQueuedEventReplayReady",
                policy.manualQueuedEventReplayReady(), "success", "warning");
        appendBooleanPolicyRow(csv, task, generatedAt, "queueLagMetricReady",
                policy.queueLagMetricReady(), "success", "warning");
        appendBooleanPolicyRow(csv, task, generatedAt, "timeoutAlertReady",
                policy.timeoutAlertReady(), "success", "warning");
        appendBooleanPolicyRow(csv, task, generatedAt, "multiInstanceLoadTestEvidenceReady",
                policy.multiInstanceLoadTestEvidenceReady(), "success", "warning");
        appendSafeExportRow(csv, task, generatedAt, "eventPayloadExported", policy.eventPayloadExported());
        appendSafeExportRow(csv, task, generatedAt, "eventIdentifierListExported", policy.eventIdentifierListExported());
        appendSafeExportRow(csv, task, generatedAt, "queueMessageBodyExported", policy.queueMessageBodyExported());
        appendSafeExportRow(csv, task, generatedAt, "recoveryDetailRowsExported", policy.recoveryDetailRowsExported());
        appendMetricRow(csv, task, generatedAt, "effectiveRecoveryBatchSize", policy.effectiveRecoveryBatchSize(), "info");
        appendMetricRow(csv, task, generatedAt, "runningTimeoutSeconds", policy.runningTimeoutSeconds(),
                policy.runningTimeoutRecoveryEnabled() ? "info" : "neutral");
        appendMetricRow(csv, task, generatedAt, "queueLagWarningSeconds", policy.queueLagWarningSeconds(),
                policy.queueLagMetricReady() ? "info" : "neutral");
        appendMetricRow(csv, task, generatedAt, "queuedTaskCount", policy.queuedTaskCount(), "info");
        appendMetricRow(csv, task, generatedAt, "runningTaskCount", policy.runningTaskCount(), "info");
        appendMetricRow(csv, task, generatedAt, "oldestQueuedAgeSeconds", policy.oldestQueuedAgeSeconds(), "warning");
        appendMetricRow(csv, task, generatedAt, "staleRunningTaskCount", policy.staleRunningTaskCount(), "warning");
        appendMetricRow(csv, task, generatedAt, "queuedStatusSignal", policy.queuedStatusSignal(), "info");
        appendMetricRow(csv, task, generatedAt, "runningStatusSignal", policy.runningStatusSignal(), "info");
        appendMetricRow(csv, task, generatedAt, "timeoutFailureSignal", policy.timeoutFailureSignal(), "warning");
        appendBooleanPolicyRow(csv, task, generatedAt, "queueLagWarning", policy.queueLagWarning(), "warning", "success");
        appendBooleanPolicyRow(csv, task, generatedAt, "timeoutWarning", policy.timeoutWarning(), "warning", "success");
        appendBooleanPolicyRow(csv, task, generatedAt, "aggregateOnly", policy.aggregateOnly(), "success", "danger");
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

    private static void appendBooleanPolicyRow(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            String metric,
            boolean value,
            String trueTone,
            String falseTone
    ) {
        appendMetadataRow(csv, task, generatedAt, metric, value, value ? trueTone : falseTone);
    }

    private static void appendSafeExportRow(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            String metric,
            boolean value
    ) {
        appendMetadataRow(csv, task, generatedAt, metric, value, value ? "danger" : null);
    }

    private static TestDesignTask taskDomainSnapshot(TestDesignTaskResponse task) {
        return new TestDesignTask(
                task.id(),
                task.projectId(),
                task.title(),
                task.status(),
                String.join(",", task.requirementIds().stream().map(UUID::toString).toList()),
                String.join(",", task.coverageTypes()),
                task.promptKey(),
                task.promptVersion(),
                task.modelInvocationId(),
                task.modelProviderName(),
                task.modelName(),
                task.totalRequirements(),
                task.generatedCount(),
                task.confirmedCount(),
                task.publishedCount(),
                task.errorMessage(),
                task.requestedBy(),
                task.idempotencyKey(),
                null,
                task.inputDigest(),
                "{}",
                task.createdAt(),
                task.updatedAt()
        );
    }
}
