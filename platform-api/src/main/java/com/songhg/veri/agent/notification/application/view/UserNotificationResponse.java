package com.songhg.veri.agent.notification.application.view;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UserNotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        String link,
        Map<String, Object> metadata,
        boolean unread,
        Instant readAt,
        Instant createdAt,
        Instant updatedAt
) {
}
