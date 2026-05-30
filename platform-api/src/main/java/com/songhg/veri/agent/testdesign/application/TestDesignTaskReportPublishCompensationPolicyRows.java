package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import java.time.Instant;
import java.util.List;

final class TestDesignTaskReportPublishCompensationPolicyRows {

    private static final String POLICY_VERSION = "wp5-publish-compensation-policy-v1";

    private TestDesignTaskReportPublishCompensationPolicyRows() {
    }

    /**
     * Appends publish replay and compensation readiness using aggregate counters only.
     *
     * <p>WP5 already supports source-key replay, partial trace-link repair and manual conflict linking. The real
     * compensation backend and cross-WP orchestration are still pending, so these rows make the gap auditable without
     * exporting candidate IDs, asset case IDs, source keys, trace details or publish error text.
     */
    static void appendRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            List<TestDesignPublishRecord> records
    ) {
        List<TestDesignPublishRecord> safeRecords = records == null ? List.of() : records;
        appendMetadataRow(csv, task, generatedAt, "policyVersion", POLICY_VERSION, null);
        appendMetadataRow(csv, task, generatedAt, "replayKeyFamily", "AI_GENERATED_CASE_KEY", null);
        appendMetadataRow(csv, task, generatedAt, "idempotentReplaySupported", true, "success");
        appendMetadataRow(csv, task, generatedAt, "partialTraceLinkRepairSupported", true, "success");
        appendMetadataRow(csv, task, generatedAt, "failedCandidateRetrySupported", true, "success");
        appendMetadataRow(csv, task, generatedAt, "manualConflictLinkSupported", true, "success");
        appendMetadataRow(csv, task, generatedAt, "asyncCompensationBackendReady", false, "warning");
        appendMetadataRow(csv, task, generatedAt, "crossWpTransactionOrchestrationReady", false, "warning");
        appendMetadataRow(csv, task, generatedAt, "candidateEvidenceExported", false, null);
        appendMetadataRow(csv, task, generatedAt, "errorTextExported", false, null);
        appendMetadataRow(csv, task, generatedAt, "caseIdentifierListExported", false, null);
        appendMetadataRow(csv, task, generatedAt, "traceDetailListExported", false, null);
        appendMetricRow(csv, task, generatedAt, "retryLinkExistingCount",
                countAction(safeRecords, "RETRY_LINK_EXISTING"), "info");
        appendMetricRow(csv, task, generatedAt, "linkExistingCount",
                countAction(safeRecords, "LINK_EXISTING"), "info");
        appendMetricRow(csv, task, generatedAt, "manualLinkExistingCount",
                countAction(safeRecords, "MANUAL_LINK_EXISTING"), "info");
        appendMetricRow(csv, task, generatedAt, "conflictCount",
                countResult(safeRecords, "CONFLICT"), "warning");
        appendMetricRow(csv, task, generatedAt, "failedCount",
                countResult(safeRecords, "FAILED"), "warning");
        appendMetadataRow(csv, task, generatedAt, "aggregateOnly", true, "success");
    }

    private static long countAction(List<TestDesignPublishRecord> records, String action) {
        return records.stream().filter(record -> action.equals(record.action())).count();
    }

    private static long countResult(List<TestDesignPublishRecord> records, String result) {
        return records.stream().filter(record -> result.equals(record.result())).count();
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
                "metadata", "publishCompensationPolicy", metric, null, value, null, tone, "fullTask", null);
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
                "summary", "publishCompensationPolicy", "metric", label, value, null,
                value > 0L ? nonZeroTone : "neutral", "fullTask", null);
    }
}
