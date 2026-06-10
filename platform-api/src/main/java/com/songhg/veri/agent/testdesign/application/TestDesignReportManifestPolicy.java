package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignReportManifestPolicyResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralizes the WP5 task-report manifest reconciliation boundary.
 *
 * <p>The report manifest proves schema version, field-set version, row count, completion status and row-integrity index
 * readiness for archive reconciliation. It intentionally avoids exporting row-level integrity values, row summaries,
 * candidate identifiers, trace identifiers and audit identifiers because those details would turn the aggregate report
 * into a sensitive operations index.
 */
public final class TestDesignReportManifestPolicy {

    public static final String POLICY_VERSION = "wp5-report-manifest-policy-v1";
    public static final String SCHEMA_VERSION = "wp5-task-report-v1";
    public static final String FIELD_SET_VERSION = "aggregate-only-v1";
    public static final String MANIFEST_MODE = "AGGREGATE_RECONCILIATION";

    private TestDesignReportManifestPolicy() {
    }

    public static TestDesignReportManifestPolicyResponse response() {
        return new TestDesignReportManifestPolicyResponse(
                POLICY_VERSION,
                SCHEMA_VERSION,
                FIELD_SET_VERSION,
                MANIFEST_MODE,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                true
        );
    }

    public static Map<String, Object> snapshot() {
        TestDesignReportManifestPolicyResponse response = response();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyVersion", response.policyVersion());
        snapshot.put("schemaVersion", response.schemaVersion());
        snapshot.put("fieldSetVersion", response.fieldSetVersion());
        snapshot.put("manifestMode", response.manifestMode());
        snapshot.put("rowCountTracked", response.rowCountTracked());
        snapshot.put("completionStatusTracked", response.completionStatusTracked());
        snapshot.put("archiveReconciliationReady", response.archiveReconciliationReady());
        snapshot.put("rowIntegrityStored", response.rowIntegrityStored());
        snapshot.put("rowIntegrityIndexReady", response.rowIntegrityIndexReady());
        snapshot.put("detailRowsExported", response.detailRowsExported());
        snapshot.put("rowIntegrityValueExported", response.rowIntegrityValueExported());
        snapshot.put("rowContentSummaryExported", response.rowContentSummaryExported());
        snapshot.put("candidateIdentifierListExported", response.candidateIdentifierListExported());
        snapshot.put("traceIdentifierListExported", response.traceIdentifierListExported());
        snapshot.put("auditIdentifierListExported", response.auditIdentifierListExported());
        snapshot.put("aggregateOnly", response.aggregateOnly());
        return snapshot;
    }
}
