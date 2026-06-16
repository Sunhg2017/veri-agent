package com.songhg.veri.agent.reporting.domain;

import java.time.Instant;
import java.util.UUID;

public record ReportEvidenceManifest(
        UUID id,
        UUID reportId,
        String sourceWp,
        String sourceType,
        String sourceRefDigest,
        String schemaVersion,
        String summaryKeysJson,
        String redactionFlagsJson,
        String evidenceSummaryJson,
        Instant createdAt
) {
}
