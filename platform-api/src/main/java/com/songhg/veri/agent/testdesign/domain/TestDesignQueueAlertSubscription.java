package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * WP5 queue alert subscription configured by operations users.
 *
 * <p>The subscription stores only routing references such as an ops-console key, email group or webhook alias. It must
 * never contain task ids, candidate ids, event payloads, trace ids or webhook secrets.</p>
 */
public record TestDesignQueueAlertSubscription(
        UUID id,
        String projectId,
        String promptKey,
        String alertType,
        String channel,
        String targetRef,
        Integer thresholdSeconds,
        boolean enabled,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
