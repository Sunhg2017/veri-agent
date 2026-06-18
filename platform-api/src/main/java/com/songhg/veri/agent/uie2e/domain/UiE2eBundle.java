package com.songhg.veri.agent.uie2e.domain;

import java.time.Instant;
import java.util.UUID;

public record UiE2eBundle(
        UUID id,
        UUID sceneId,
        String projectId,
        String status,
        String bundleDigest,
        String specSummaryJson,
        String fixtureSummaryJson,
        String staticCheckSummaryJson,
        String submittedBy,
        String approvedBy,
        Instant submittedAt,
        Instant approvedAt,
        Instant rejectedAt,
        String createdBy,
        String updatedBy,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
