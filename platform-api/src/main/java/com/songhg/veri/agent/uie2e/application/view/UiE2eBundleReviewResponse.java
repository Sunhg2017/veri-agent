package com.songhg.veri.agent.uie2e.application.view;

import java.time.Instant;
import java.util.UUID;

public record UiE2eBundleReviewResponse(
        UUID id,
        String reviewStatus,
        String reviewComment,
        String reviewedBy,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
