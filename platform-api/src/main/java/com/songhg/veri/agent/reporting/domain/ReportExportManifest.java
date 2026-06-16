package com.songhg.veri.agent.reporting.domain;

import java.time.Instant;
import java.util.UUID;

public record ReportExportManifest(
        UUID id,
        UUID reportId,
        String exportType,
        String status,
        String schemaVersion,
        String fieldSetVersion,
        String redactionPolicyJson,
        String contentDigest,
        boolean aggregateOnly,
        String exportedBy,
        Instant exportedAt,
        String blockReason,
        Instant createdAt
) {
}
