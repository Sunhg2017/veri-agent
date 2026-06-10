package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Line-level integrity index for a stored aggregate report archive.
 *
 * <p>Only row number, SHA-256 digests and fixed section metadata are stored. The row content and business identifiers
 * are intentionally excluded so integrity checks do not become a sensitive detail index.</p>
 */
public record TestDesignReportArchiveLineIntegrity(
        UUID archiveId,
        int rowNumber,
        String rowDigest,
        String previousRowDigest,
        String chainDigest,
        String recordType,
        String section,
        String metric,
        Instant createdAt
) {
}
