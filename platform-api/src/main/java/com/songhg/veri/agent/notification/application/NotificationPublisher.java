package com.songhg.veri.agent.notification.application;

import java.util.Map;
import java.util.UUID;

public interface NotificationPublisher {

    void publishToUser(
            UUID userId,
            String type,
            String title,
            String body,
            String link,
            Map<String, Object> metadata
    );
}
