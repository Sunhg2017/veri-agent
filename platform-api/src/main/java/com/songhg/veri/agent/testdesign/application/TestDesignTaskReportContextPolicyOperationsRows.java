package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignContextPolicyOperationsResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class TestDesignTaskReportContextPolicyOperationsRows {

    private TestDesignTaskReportContextPolicyOperationsRows() {
    }

    /**
     * Appends the task-time context-policy operations snapshot without exporting override rules or approval notes.
     */
    static void appendRows(StringBuilder csv, TestDesignTaskResponse task, Instant generatedAt) {
        Map<String, Object> snapshot = taskPolicyOperations(task);
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

    private static Map<String, Object> taskPolicyOperations(TestDesignTaskResponse task) {
        Map<String, Object> snapshot = new LinkedHashMap<>(TestDesignContextPolicyOperations.snapshot());
        if (task != null && task.contextSummary() != null) {
            Object value = task.contextSummary().get("policyOperations");
            if (value instanceof Map<?, ?> map) {
                map.forEach((key, entryValue) -> snapshot.put(String.valueOf(key), entryValue));
            }
        }
        TestDesignContextPolicyOperationsResponse operations = task == null ? null : task.contextPolicyOperations();
        if (operations != null) {
            snapshot.put("policyVersion", operations.policyVersion());
            snapshot.put("operationMode", operations.operationMode());
            snapshot.put("policyResolutionOrder", operations.policyResolutionOrder());
            snapshot.put("policyFallbackBehavior", operations.policyFallbackBehavior());
            snapshot.put("approvalStatus", operations.approvalStatus());
            snapshot.put("projectOverrideStoreReady", operations.projectOverrideStoreReady());
            snapshot.put("environmentOverrideStoreReady", operations.environmentOverrideStoreReady());
            snapshot.put("changeApprovalWorkflowReady", operations.changeApprovalWorkflowReady());
            snapshot.put("effectivePolicySnapshotMaterialized", operations.effectivePolicySnapshotMaterialized());
            snapshot.put("aggregateOnly", operations.aggregateOnly());
        }
        return snapshot;
    }
}
