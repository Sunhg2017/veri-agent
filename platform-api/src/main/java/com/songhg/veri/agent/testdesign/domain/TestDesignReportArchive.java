package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Stored WP5 aggregate task-report archive.
 *
 * <p>The archive content is the safety-scanned aggregate CSV returned by the export endpoint. Operations views must
 * expose only metadata, digest, counts and approval state; storage keys and raw content stay server-side.</p>
 */
public record TestDesignReportArchive(
        UUID id,
        UUID manifestId,
        UUID taskId,
        String projectId,
        String storageBackend,
        String storageKey,
        String contentDigest,
        long contentSizeBytes,
        long reportRowCount,
        long lineIntegrityCount,
        String status,
        String archiveApprovalStatus,
        String externalApprovalStatus,
        Instant retentionUntil,
        byte[] contentBytes,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
