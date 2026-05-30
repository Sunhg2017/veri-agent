package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignReportManifestPolicyResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import java.time.Instant;

final class TestDesignTaskReportManifestPolicyRows {

    private TestDesignTaskReportManifestPolicyRows() {
    }

    /**
     * Appends manifest reconciliation policy without exporting row-level indexes or identifiers.
     *
     * <p>The rows below prove that a report can be checked for schema, field-set, row count and completion status. They
     * must not include row-level integrity values, row summaries, candidate IDs, trace IDs or audit IDs because those
     * values can indirectly disclose project structure or operational traces.
     */
    static void appendRows(StringBuilder csv, TestDesignTaskResponse task, Instant generatedAt) {
        TestDesignReportManifestPolicyResponse policy = task.reportManifestPolicy() == null
                ? TestDesignReportManifestPolicy.response()
                : task.reportManifestPolicy();
        appendMetadataRow(csv, task, generatedAt, "policyVersion", policy.policyVersion(), null);
        appendMetadataRow(csv, task, generatedAt, "schemaVersion", policy.schemaVersion(), null);
        appendMetadataRow(csv, task, generatedAt, "fieldSetVersion", policy.fieldSetVersion(), null);
        appendMetadataRow(csv, task, generatedAt, "manifestMode", policy.manifestMode(), null);
        appendMetadataRow(csv, task, generatedAt, "rowCountTracked",
                policy.rowCountTracked(), policy.rowCountTracked() ? "success" : "danger");
        appendMetadataRow(csv, task, generatedAt, "completionStatusTracked",
                policy.completionStatusTracked(), policy.completionStatusTracked() ? "success" : "danger");
        appendMetadataRow(csv, task, generatedAt, "archiveReconciliationReady",
                policy.archiveReconciliationReady(), policy.archiveReconciliationReady() ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "detailRowsExported", policy.detailRowsExported(), null);
        appendMetadataRow(csv, task, generatedAt, "rowIntegrityValueExported",
                policy.rowIntegrityValueExported(), null);
        appendMetadataRow(csv, task, generatedAt, "rowContentSummaryExported",
                policy.rowContentSummaryExported(), null);
        appendMetadataRow(csv, task, generatedAt, "candidateIdentifierListExported",
                policy.candidateIdentifierListExported(), null);
        appendMetadataRow(csv, task, generatedAt, "traceIdentifierListExported",
                policy.traceIdentifierListExported(), null);
        appendMetadataRow(csv, task, generatedAt, "auditIdentifierListExported",
                policy.auditIdentifierListExported(), null);
        appendMetadataRow(csv, task, generatedAt, "aggregateOnly", policy.aggregateOnly(), "success");
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
                "metadata", "reportManifestPolicy", metric, null, value, null, tone, "fullTask", null);
    }
}
