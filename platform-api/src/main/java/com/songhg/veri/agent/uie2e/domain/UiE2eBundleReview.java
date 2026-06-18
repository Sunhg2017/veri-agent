package com.songhg.veri.agent.uie2e.domain;

import java.time.Instant;
import java.util.UUID;

public record UiE2eBundleReview(
        UUID id,
        UUID bundleId,
        String projectId,
        String reviewStatus,
        String reviewComment,
        String reviewedBy,
        Instant reviewedAt,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
