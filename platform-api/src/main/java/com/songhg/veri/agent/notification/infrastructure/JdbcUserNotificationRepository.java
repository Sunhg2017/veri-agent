package com.songhg.veri.agent.notification.infrastructure;

import com.songhg.veri.agent.notification.application.port.UserNotificationRepository;
import com.songhg.veri.agent.notification.application.query.NotificationQuery;
import com.songhg.veri.agent.notification.domain.UserNotification;
import com.songhg.veri.agent.notification.infrastructure.mapper.NotificationMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("db")
@Repository
public class JdbcUserNotificationRepository implements UserNotificationRepository {

    private final NotificationMapper mapper;

    public JdbcUserNotificationRepository(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UserNotification insert(UserNotification notification) {
        mapper.insert(notification);
        return notification;
    }

    @Override
    public List<UserNotification> notifications(UUID userId, NotificationQuery query) {
        return mapper.notifications(userId, query);
    }

    @Override
    public long countNotifications(UUID userId, NotificationQuery query) {
        return mapper.countNotifications(userId, query);
    }

    @Override
    public long countUnread(UUID userId) {
        return mapper.countUnread(userId);
    }

    @Override
    public Optional<UserNotification> notification(UUID id) {
        return Optional.ofNullable(mapper.notification(id));
    }

    @Override
    public boolean markRead(UUID id, UUID userId, Instant readAt) {
        return mapper.markRead(id, userId, readAt) == 1;
    }

    @Override
    public int markAllRead(UUID userId, Instant readAt) {
        return mapper.markAllRead(userId, readAt);
    }
}
