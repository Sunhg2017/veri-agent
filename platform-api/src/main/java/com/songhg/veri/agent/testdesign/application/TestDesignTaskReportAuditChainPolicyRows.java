package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditChainPolicyResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import java.time.Instant;
import java.util.List;
import org.springframework.util.StringUtils;

final class TestDesignTaskReportAuditChainPolicyRows {

    private TestDesignTaskReportAuditChainPolicyRows() {
    }

    /**
     * Appends audit-chain observability boundaries without exporting audit/event identifiers.
     *
     * <p>The full task report needs to show whether WP5 can be reconciled through task, review, publish, model and WP1
     * audit signals. It must not become a raw audit-log export, so these rows only include fixed policy flags and
     * aggregate counts; audit rows, trace IDs, invocation IDs, sourceRef values, asset IDs and candidate ID lists stay
     * out of the CSV.
     */
    static void appendRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            List<TestDesignReviewRecord> reviewRecords,
            List<TestDesignPublishRecord> publishRecords
    ) {
        TestDesignAuditChainPolicyResponse policy = task.auditChainPolicy() == null
                ? TestDesignAuditChainPolicy.response()
                : task.auditChainPolicy();
        appendMetadataRow(csv, task, generatedAt, "policyVersion", policy.policyVersion(), null);
        appendMetadataRow(csv, task, generatedAt, "chainMode", policy.chainMode(), "warning");
        appendMetadataRow(csv, task, generatedAt, "eventSource", policy.eventSource(), null);
        appendMetadataRow(csv, task, generatedAt, "wp1AuditEventWritten",
                policy.wp1AuditEventWritten(), policy.wp1AuditEventWritten() ? "success" : "danger");
        appendMetadataRow(csv, task, generatedAt, "wp2InvocationReferenceTracked",
                policy.wp2InvocationReferenceTracked(), trackedTone(task.modelInvocationId() != null));
        appendMetadataRow(csv, task, generatedAt, "wp3PublishReferenceTracked",
                policy.wp3PublishReferenceTracked(), trackedTone(!publishRecords.isEmpty()));
        appendMetadataRow(csv, task, generatedAt, "wp5DomainEventsTracked",
                policy.wp5DomainEventsTracked(), policy.wp5DomainEventsTracked() ? "success" : "danger");
        appendMetadataRow(csv, task, generatedAt, "projectScopeRequired",
                policy.projectScopeRequired(), policy.projectScopeRequired() ? "success" : "danger");
        appendMetadataRow(csv, task, generatedAt, "traceSignalTracked",
                policy.traceSignalTracked(), trackedTone(hasTraceSignal(task)));
        appendMetadataRow(csv, task, generatedAt, "crossWpAuditDashboardReady",
                policy.crossWpAuditDashboardReady(), policy.crossWpAuditDashboardReady() ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "auditOutboxReplayDashboardReady",
                policy.auditOutboxReplayDashboardReady(),
                policy.auditOutboxReplayDashboardReady() ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "auditEventDetailExported", policy.auditEventDetailExported(), null);
        appendMetadataRow(csv, task, generatedAt, "candidateIdentifierListExported",
                policy.candidateIdentifierListExported(), null);
        appendMetadataRow(csv, task, generatedAt, "platformAuditIdentifierExported",
                policy.platformAuditIdentifierExported(), null);
        appendMetadataRow(csv, task, generatedAt, "traceIdValueExported", policy.traceIdValueExported(), null);
        appendMetadataRow(csv, task, generatedAt, "modelInvocationIdValueExported",
                policy.modelInvocationIdValueExported(), null);
        appendMetadataRow(csv, task, generatedAt, "publishIdentifierValueExported",
                policy.publishIdentifierValueExported(), null);
        appendSummaryRow(csv, task, generatedAt, "taskEventCount", 1L, "info");
        appendSummaryRow(csv, task, generatedAt, "reviewEventCount", reviewRecords.size(),
                reviewRecords.isEmpty() ? "neutral" : "info");
        appendSummaryRow(csv, task, generatedAt, "publishEventCount", publishRecords.size(),
                publishRecords.isEmpty() ? "neutral" : "info");
        appendSummaryRow(csv, task, generatedAt, "noteCoverageCount", noteCoverageCount(reviewRecords, publishRecords),
                noteCoverageCount(reviewRecords, publishRecords) > 0L ? "info" : "neutral");
        appendMetadataRow(csv, task, generatedAt, "aggregateOnly", policy.aggregateOnly(), "success");
    }

    private static boolean hasTraceSignal(TestDesignTaskResponse task) {
        return task.modelObservation() != null && StringUtils.hasText(task.modelObservation().traceId());
    }

    private static String trackedTone(boolean actualSignalPresent) {
        return actualSignalPresent ? "success" : "neutral";
    }

    private static long noteCoverageCount(
            List<TestDesignReviewRecord> reviewRecords,
            List<TestDesignPublishRecord> publishRecords
    ) {
        return reviewRecords.stream().filter(record -> StringUtils.hasText(record.comment())).count()
                + publishRecords.stream().filter(record -> StringUtils.hasText(record.errorMessage())).count();
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
                "metadata", "auditChainPolicy", metric, null, value, null, tone, "fullTask", null);
    }

    private static void appendSummaryRow(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            String label,
            Object value,
            String tone
    ) {
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "summary", "auditChainPolicy", "metric", label, value, null, tone, "fullTask", null);
    }
}
