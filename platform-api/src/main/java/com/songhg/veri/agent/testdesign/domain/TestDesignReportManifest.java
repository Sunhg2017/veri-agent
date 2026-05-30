package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * WP5 task-report manifest persisted after a safe aggregate report export.
 *
 * <p>The manifest stores only schema, row-count and digest metadata for archive reconciliation. It must not contain
 * report rows, candidate identifiers, trace identifiers, audit identifiers or CSV content.
 */
public record TestDesignReportManifest(
        /** Manifest record ID. */
        UUID id,
        /** Exported task ID. */
        UUID taskId,
        /** Owning project ID for scope checks and operations queries. */
        String projectId,
        /** Report schema version used by the exported CSV. */
        String schemaVersion,
        /** Aggregate field-set version used by the exported CSV. */
        String fieldSetVersion,
        /** Manifest reconciliation mode. */
        String manifestMode,
        /** Data row count captured immediately before manifest rows are appended. */
        long rowCountBeforeManifest,
        /** Data row count after manifest rows are appended. */
        long reportRowCount,
        /** Whether the manifest belongs to an aggregate-only report. */
        boolean aggregateOnly,
        /** Whether report detail rows are exported. */
        boolean detailRowsExported,
        /** Manifest completion status. */
        String manifestStatus,
        /** SHA-256 digest of the returned CSV content; the raw report content is never stored. */
        String contentDigest,
        /** Timestamp embedded in the exported report. */
        Instant generatedAt,
        /** Manifest persistence time. */
        Instant createdAt
) {
}
