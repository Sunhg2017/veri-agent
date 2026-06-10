package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import java.time.Instant;
import java.util.Map;

final class TestDesignTaskReportScopePolicyRows {

    private TestDesignTaskReportScopePolicyRows() {
    }

    /**
     * Appends permission and resource-scope boundaries without exporting role matrices or identifier lists.
     *
     * <p>The report proves which project-scoped checks are expected for WP5 operations while keeping candidate IDs,
     * role-rule details and service-token values out of archived CSV rows.
     */
    static void appendRows(StringBuilder csv, TestDesignTaskResponse task, Instant generatedAt) {
        Map<String, Object> snapshot = TestDesignScopePolicy.snapshot();
        appendMetadataRow(csv, task, generatedAt, "policyVersion", snapshot.get("policyVersion"), null);
        appendMetadataRow(csv, task, generatedAt, "scopeModel", snapshot.get("scopeModel"), "success");
        appendMetadataRow(csv, task, generatedAt, "listFallbackScope", snapshot.get("listFallbackScope"), "warning");
        appendMetadataRow(csv, task, generatedAt, "taskProjectScopeRequired",
                snapshot.get("taskProjectScopeRequired"), "success");
        appendMetadataRow(csv, task, generatedAt, "candidateProjectScopeRequired",
                snapshot.get("candidateProjectScopeRequired"), "success");
        appendMetadataRow(csv, task, generatedAt, "batchCandidateProjectScopeRequired",
                snapshot.get("batchCandidateProjectScopeRequired"), "success");
        appendMetadataRow(csv, task, generatedAt, "publishProjectScopeRequired",
                snapshot.get("publishProjectScopeRequired"), "success");
        appendMetadataRow(csv, task, generatedAt, "asyncTaskProjectScopeRecovered",
                snapshot.get("asyncTaskProjectScopeRecovered"), "success");
        appendMetadataRow(csv, task, generatedAt, "smokeProjectScopeRequired",
                snapshot.get("smokeProjectScopeRequired"), "success");
        appendMetadataRow(csv, task, generatedAt, "evaluationCorpusProjectIsolated",
                snapshot.get("evaluationCorpusProjectIsolated"), "success");
        appendMetadataRow(csv, task, generatedAt, "evaluationCorpusOperationsReady",
                snapshot.get("evaluationCorpusOperationsReady"), readyTone(snapshot.get("evaluationCorpusOperationsReady")));
        appendMetadataRow(csv, task, generatedAt, "crossWpScopeDashboardReady",
                snapshot.get("crossWpScopeDashboardReady"), readyTone(snapshot.get("crossWpScopeDashboardReady")));
        appendMetadataRow(csv, task, generatedAt, "candidateIdentifierListExported",
                snapshot.get("candidateIdentifierListExported"), null);
        appendMetadataRow(csv, task, generatedAt, "roleRuleDetailExported",
                snapshot.get("roleRuleDetailExported"), null);
        appendMetadataRow(csv, task, generatedAt, "serviceTokenValueExported",
                snapshot.get("serviceTokenValueExported"), null);
        appendMetadataRow(csv, task, generatedAt, "aggregateOnly", snapshot.get("aggregateOnly"), "success");
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
                "metadata", "scopePolicy", metric, null, value, null, tone, "fullTask", null);
    }

    private static String readyTone(Object value) {
        return Boolean.TRUE.equals(value) ? "success" : "warning";
    }
}
