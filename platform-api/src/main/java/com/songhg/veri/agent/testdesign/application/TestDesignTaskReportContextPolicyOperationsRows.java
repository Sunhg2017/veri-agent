package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import java.time.Instant;
import java.util.Map;

final class TestDesignTaskReportContextPolicyOperationsRows {

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
        Map<String, Object> snapshot = TestDesignContextPolicyOperations.snapshot();
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "policyVersion", null,
                snapshot.get("policyVersion"), null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "operationMode", null,
                snapshot.get("operationMode"), null, "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "policyResolutionOrder", null,
                snapshot.get("policyResolutionOrder"), null, "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "policyFallbackBehavior", null,
                snapshot.get("policyFallbackBehavior"), null, "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "approvalStatus", null,
                snapshot.get("approvalStatus"), null, "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "projectOverrideStoreReady", null,
                snapshot.get("projectOverrideStoreReady"), null, "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "environmentOverrideStoreReady", null,
                snapshot.get("environmentOverrideStoreReady"), null, "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "changeApprovalWorkflowReady", null,
                snapshot.get("changeApprovalWorkflowReady"), null, "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "effectivePolicySnapshotMaterialized", null,
                snapshot.get("effectivePolicySnapshotMaterialized"), null, "success", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "policyDiffPreviewExported", null,
                snapshot.get("policyDiffPreviewExported"), null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "approvalNotesExported", null,
                snapshot.get("approvalNotesExported"), null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "ticketUrlExported", null,
                snapshot.get("ticketUrlExported"), null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "projectOverrideRulesExported", null,
                snapshot.get("projectOverrideRulesExported"), null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "environmentOverrideRulesExported", null,
                snapshot.get("environmentOverrideRulesExported"), null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyOperations", "aggregateOnly", null,
                snapshot.get("aggregateOnly"), null, "success", "fullTask", null);
    }
}
