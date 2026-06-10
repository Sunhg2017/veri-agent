package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Bounded note in a report archive approval work-order timeline.
 */
public record TestDesignReportArchiveNote(
        UUID id,
        UUID approvalId,
        String noteType,
        String noteText,
        String createdBy,
        Instant createdAt
) {
}
