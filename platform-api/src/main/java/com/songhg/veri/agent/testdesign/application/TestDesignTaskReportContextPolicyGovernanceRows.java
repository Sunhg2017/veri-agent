package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignContextPolicyGovernanceResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import java.time.Instant;

final class TestDesignTaskReportContextPolicyGovernanceRows {

    private TestDesignTaskReportContextPolicyGovernanceRows() {
    }

    /**
     * Makes the enterprise governance gap explicit without exporting project IDs, approval notes or policy documents.
     *
     * <p>WP5 currently enforces platform default clipping limits. These rows document that project/environment
     * overrides and approval workflow are still not operational, so archive readers do not mistake the default policy
     * snapshot for a fully delegated operations console.
     */
    static void appendRows(StringBuilder csv, TestDesignTaskResponse task, Instant generatedAt) {
        TestDesignContextPolicyGovernanceResponse governance = task.contextPolicyGovernance() == null
                ? TestDesignContextPolicyGovernance.response()
                : task.contextPolicyGovernance();
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyGovernance", "policyVersion", null,
                governance.policyVersion(), null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyGovernance", "policySource", null,
                governance.policySource(), null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyGovernance", "governanceStatus", null,
                governance.governanceStatus(), null, "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyGovernance", "changeMode", null,
                governance.changeMode(), null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyGovernance", "projectOverrideSupported", null,
                governance.projectOverrideSupported(), null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyGovernance", "environmentOverrideSupported", null,
                governance.environmentOverrideSupported(), null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyGovernance", "changeApprovalRequired", null,
                governance.changeApprovalRequired(), null, null, "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyGovernance", "changeApprovalWorkflowReady", null,
                governance.changeApprovalWorkflowReady(), null, "warning", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyGovernance", "effectiveAtTaskCreation", null,
                governance.effectiveAtTaskCreation(), null, "success", "fullTask", null);
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextPolicyGovernance", "aggregateOnly", null,
                governance.aggregateOnly(), null, "success", "fullTask", null);
    }
}
