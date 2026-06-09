package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Timeline note attached to a WP5 context policy approval work order.
 *
 * <p>Notes are for operator collaboration only and must not contain source context, raw prompts, credentials or model
 * payload snippets. The service validates the content before persistence.
 */
public record TestDesignContextPolicyNote(
        UUID id,
        UUID overrideId,
        String noteType,
        String noteText,
        String createdBy,
        Instant createdAt
) {
}
