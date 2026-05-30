package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReleaseReadinessPolicyResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import java.time.Instant;

final class TestDesignTaskReportReleaseReadinessPolicyRows {

    private TestDesignTaskReportReleaseReadinessPolicyRows() {
    }

    /**
     * Appends release-readiness approval boundaries without changing publish authorization semantics.
     *
     * <p>Quality readiness remains advisory in WP5. These rows make the missing publish-blocking approval workflow
     * visible to release reviewers while keeping candidate evidence, threshold rule details and approval notes out of
     * the exported report.
     */
    static void appendRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            TestDesignQualityReadinessResponse readiness
    ) {
        TestDesignReleaseReadinessPolicyResponse policy = task.releaseReadinessPolicy() == null
                ? TestDesignReleaseReadinessPolicy.response()
                : task.releaseReadinessPolicy();
        appendMetadataRow(csv, task, generatedAt, "policyVersion", policy.policyVersion(), null);
        appendMetadataRow(csv, task, generatedAt, "decisionMode", policy.decisionMode(), "warning");
        appendMetadataRow(csv, task, generatedAt, "thresholdSource", policy.thresholdSource(), null);
        appendMetadataRow(csv, task, generatedAt, "qualityThresholdEvaluated",
                policy.qualityThresholdEvaluated(), "success");
        appendMetadataRow(csv, task, generatedAt, "advisoryOnly", policy.advisoryOnly(), "warning");
        appendMetadataRow(csv, task, generatedAt, "publishBlockingEnabled",
                policy.publishBlockingEnabled(), policy.publishBlockingEnabled() ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "manualApprovalRequired", policy.manualApprovalRequired(), null);
        appendMetadataRow(csv, task, generatedAt, "approvalWorkflowReady",
                policy.approvalWorkflowReady(), policy.approvalWorkflowReady() ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "autoPublishAllowed",
                policy.autoPublishAllowed(), policy.autoPublishAllowed() ? "danger" : "success");
        appendMetadataRow(csv, task, generatedAt, "confirmedCandidateRequired",
                policy.confirmedCandidateRequired(), policy.confirmedCandidateRequired() ? "success" : "danger");
        appendMetadataRow(csv, task, generatedAt, "qualityGateOverrideSupported",
                policy.qualityGateOverrideSupported(), policy.qualityGateOverrideSupported() ? "warning" : null);
        appendMetadataRow(csv, task, generatedAt, "candidateEvidenceExported",
                policy.candidateEvidenceExported(), null);
        appendMetadataRow(csv, task, generatedAt, "approvalNotesExported", policy.approvalNotesExported(), null);
        appendMetadataRow(csv, task, generatedAt, "thresholdRuleDetailExported",
                policy.thresholdRuleDetailExported(), null);
        if (readiness != null) {
            appendSummaryRow(csv, task, generatedAt, "readinessStatus", readiness.status(),
                    readinessTone(readiness.status()));
            appendSummaryRow(csv, task, generatedAt, "blockingCount", readiness.blockingCount(),
                    readiness.blockingCount() > 0L ? "warning" : "neutral");
            appendSummaryRow(csv, task, generatedAt, "warningCount", readiness.warningCount(),
                    readiness.warningCount() > 0L ? "warning" : "neutral");
        }
        appendMetadataRow(csv, task, generatedAt, "aggregateOnly", policy.aggregateOnly(), "success");
    }

    private static String readinessTone(String status) {
        return "PASSED".equals(status) ? "success" : "warning";
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
                "metadata", "releaseReadinessPolicy", metric, null, value, null, tone, "fullTask", null);
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
                "summary", "releaseReadinessPolicy", "metric", label, value, null, tone, "fullTask", null);
    }
}
