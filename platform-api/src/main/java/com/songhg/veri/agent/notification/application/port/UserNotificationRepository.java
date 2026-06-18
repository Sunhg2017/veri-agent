package com.songhg.veri.agent.notification.application.port;

import com.songhg.veri.agent.notification.application.query.NotificationQuery;
import com.songhg.veri.agent.notification.domain.UserNotification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserNotificationRepository {

    UserNotification insert(UserNotification notification);

    List<UserNotification> notifications(UUID userId, NotificationQuery query);

    long countNotifications(UUID userId, NotificationQuery query);

    long countUnread(UUID userId);

    Optional<UserNotification> notification(UUID id);

    boolean markRead(UUID id, UUID userId, Instant readAt);

    int markAllRead(UUID userId, Instant readAt);
}
