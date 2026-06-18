package com.songhg.veri.agent.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record UserNotification(
        UUID id,
        UUID userId,
        String type,
        String title,
        String body,
        String link,
        String metadataJson,
        Instant readAt,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {

    public boolean unread() {
        return readAt == null;
    }
}
