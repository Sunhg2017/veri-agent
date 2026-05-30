package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessCheckResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReleaseReadinessPolicyResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import java.time.Instant;

final class TestDesignTaskReportReadinessPolicyRows {

    private static final String POLICY_VERSION = "wp5-quality-readiness-policy-v1";
    private static final String THRESHOLD_SOURCE = "DEPLOY_CONFIG";

    private TestDesignTaskReportReadinessPolicyRows() {
    }

    /**
     * Appends aggregate readiness thresholds for release review without exporting candidate-level evidence.
     *
     * <p>The rows expose only fixed policy metadata, status counters, the configured publish-blocking boundary and
     * bounded numeric threshold values. They must not be treated as a manual approval record or override workflow.
     */
    static void appendRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            TestDesignQualityReadinessResponse readiness
    ) {
        TestDesignReleaseReadinessPolicyResponse releasePolicy = task.releaseReadinessPolicy() == null
                ? TestDesignReleaseReadinessPolicy.response()
                : task.releaseReadinessPolicy();
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "readinessPolicy", "policyVersion", null,
                POLICY_VERSION, null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "readinessPolicy", "thresholdSource", null,
                THRESHOLD_SOURCE, null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "readinessPolicy", "advisoryOnly", null,
                releasePolicy.advisoryOnly(), null, releasePolicy.advisoryOnly() ? "warning" : "success",
                "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "readinessPolicy", "publishBlockingEnabled", null,
                releasePolicy.publishBlockingEnabled(), null,
                releasePolicy.publishBlockingEnabled() ? "success" : "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "summary", "readinessPolicy", "readinessStatus", null,
                readiness.status(), null, readinessTone(readiness.status()), "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "summary", "readinessPolicy", "metric", "blockingCount",
                readiness.blockingCount(), null, readiness.blockingCount() > 0L ? "warning" : "neutral",
                "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "summary", "readinessPolicy", "metric", "warningCount",
                readiness.warningCount(), null, readiness.warningCount() > 0L ? "warning" : "neutral",
                "fullTask", null);
        for (TestDesignQualityReadinessCheckResponse check : readiness.checks()) {
            appendCheckRows(csv, task, generatedAt, check);
        }
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "readinessPolicy", "aggregateOnly", null,
                true, null, "success", "fullTask", null);
    }

    private static void appendCheckRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            TestDesignQualityReadinessCheckResponse check
    ) {
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "summary", "readinessPolicy", "checkStatus", check.code(),
                check.status(), null, readinessTone(check.status()), "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "summary", "readinessPolicy", "currentValue", check.code(),
                check.currentValue(), null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "summary", "readinessPolicy", "thresholdValue", check.code(),
                check.thresholdValue(), null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "readinessPolicy", "unit", check.code(),
                check.unit(), null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "readinessPolicy", "severity", check.code(),
                check.severity(), null, null, "fullTask", null);
    }

    private static String readinessTone(String status) {
        return "PASSED".equals(status) ? "success" : "warning";
    }
}
