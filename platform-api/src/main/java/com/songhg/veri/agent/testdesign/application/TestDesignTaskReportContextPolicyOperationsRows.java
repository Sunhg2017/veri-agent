package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import java.time.Instant;

final class TestDesignTaskReportContextPolicyOperationsRows {

    private static final String POLICY_VERSION = "wp5-context-policy-operations-v1";
    private static final String OPERATION_MODE = "PLATFORM_DEFAULT_ONLY";

    private TestDesignTaskReportContextPolicyOperationsRows() {
    }

    /**
     * Appends context-policy operations readiness using fixed flags only.
     *
     * <p>WP5 currently materializes the effective platform default policy at task creation time, but the delegated
     * project/environment policy stores and approval workflow are not available. These rows keep that operational gap
     * visible in archived reports without exporting policy bodies, override rules, approval notes or ticket URLs.
     */
    static void appendRows(StringBuilder csv, TestDesignTaskResponse task, Instant generatedAt) {
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "policyVersion", null,
                POLICY_VERSION, null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "operationMode", null,
                OPERATION_MODE, null, "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "projectOverrideStoreReady", null,
                false, null, "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "environmentOverrideStoreReady", null,
                false, null, "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "changeApprovalWorkflowReady", null,
                false, null, "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "effectivePolicySnapshotMaterialized", null,
                true, null, "success", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "policyDiffPreviewExported", null,
                false, null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "approvalNotesExported", null,
                false, null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "ticketUrlExported", null,
                false, null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "projectOverrideRulesExported", null,
                false, null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "environmentOverrideRulesExported", null,
                false, null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "aggregateOnly", null,
                true, null, "success", "fullTask", null);
    }
}
