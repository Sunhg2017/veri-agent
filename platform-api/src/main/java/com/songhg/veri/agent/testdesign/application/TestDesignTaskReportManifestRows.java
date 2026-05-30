package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignReportManifestPolicyResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import java.time.Instant;
import org.springframework.util.StringUtils;

final class TestDesignTaskReportManifestRows {

    private TestDesignTaskReportManifestRows() {
    }

    /**
     * Adds a bounded report manifest for archive reconciliation without listing row contents or identifiers.
     *
     * <p>The row count is captured immediately before the manifest is appended, so archive tooling can detect truncated
     * exports while the report still avoids candidate bodies, audit records, trace IDs and other detail payloads.
     */
    static void appendRows(StringBuilder csv, TestDesignTaskResponse task, Instant generatedAt) {
        long rowCountBeforeManifest = reportDataRowCount(csv);
        TestDesignReportManifestPolicyResponse policy = task.reportManifestPolicy() == null
                ? TestDesignReportManifestPolicy.response()
                : task.reportManifestPolicy();
        appendMetadataRow(csv, task, generatedAt, "schemaVersion", policy.schemaVersion(), null);
        appendMetadataRow(csv, task, generatedAt, "fieldSetVersion", policy.fieldSetVersion(), null);
        appendMetadataRow(csv, task, generatedAt, "rowCountBeforeManifest", rowCountBeforeManifest, null);
        appendMetadataRow(csv, task, generatedAt, "aggregateOnly", policy.aggregateOnly(), "success");
        appendMetadataRow(csv, task, generatedAt, "detailRowsExported", policy.detailRowsExported(), null);
        appendMetadataRow(csv, task, generatedAt, "manifestStatus", "COMPLETE", "success");
    }

    private static long reportDataRowCount(StringBuilder csv) {
        String content = csv.toString();
        long nonBlankRows = content.lines().filter(StringUtils::hasText).count();
        if (nonBlankRows == 0L) {
            return 0L;
        }
        return content.startsWith("recordType,") ? nonBlankRows - 1L : nonBlankRows;
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
                "metadata", "reportManifest", metric, null, value, null, tone, "fullTask", null);
    }
}
