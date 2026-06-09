package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Timeline note attached to a WP5 release-readiness approval work order.
 */
public record TestDesignReleaseReadinessNote(
        UUID id,
        UUID approvalId,
        String noteType,
        String noteText,
        String createdBy,
        Instant createdAt
) {
}
