package com.songhg.veri.agent.notification.infrastructure;

import com.songhg.veri.agent.notification.application.port.UserNotificationRepository;
import com.songhg.veri.agent.notification.application.query.NotificationQuery;
import com.songhg.veri.agent.notification.domain.UserNotification;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Profile("local")
@Primary
@Repository
public class InMemoryUserNotificationRepository implements UserNotificationRepository {

    private final ConcurrentHashMap<UUID, UserNotification> notifications = new ConcurrentHashMap<>();

    @Override
    public UserNotification insert(UserNotification notification) {
        notifications.put(notification.id(), notification);
        return notification;
    }

    @Override
    public List<UserNotification> notifications(UUID userId, NotificationQuery query) {
        return filtered(userId, query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countNotifications(UUID userId, NotificationQuery query) {
        return filtered(userId, query).count();
    }

    @Override
    public long countUnread(UUID userId) {
        return notifications.values().stream()
                .filter(item -> userId.equals(item.userId()))
                .filter(UserNotification::unread)
                .count();
    }

    @Override
    public Optional<UserNotification> notification(UUID id) {
        return Optional.ofNullable(notifications.get(id));
    }

    @Override
    public boolean markRead(UUID id, UUID userId, Instant readAt) {
        synchronized (notifications) {
            UserNotification current = notifications.get(id);
            if (current == null || !userId.equals(current.userId()) || !current.unread()) {
                return current != null && !current.unread() && userId.equals(current.userId());
            }
            notifications.put(id, new UserNotification(
                    current.id(),
                    current.userId(),
                    current.type(),
                    current.title(),
                    current.body(),
                    current.link(),
                    current.metadataJson(),
                    readAt,
                    current.createdBy(),
                    current.createdAt(),
                    readAt
            ));
            return true;
        }
    }

    @Override
    public int markAllRead(UUID userId, Instant readAt) {
        int updated = 0;
        for (UserNotification current : notifications.values()) {
            if (userId.equals(current.userId()) && current.unread()) {
                notifications.put(current.id(), new UserNotification(
                        current.id(),
                        current.userId(),
                        current.type(),
                        current.title(),
                        current.body(),
                        current.link(),
                        current.metadataJson(),
                        readAt,
                        current.createdBy(),
                        current.createdAt(),
                        readAt
                ));
                updated++;
            }
        }
        return updated;
    }

    private Stream<UserNotification> filtered(UUID userId, NotificationQuery query) {
        Stream<UserNotification> stream = notifications.values().stream()
                .filter(item -> userId.equals(item.userId()));
        if (StringUtils.hasText(query.status())) {
            if ("UNREAD".equals(query.status())) {
                stream = stream.filter(UserNotification::unread);
            } else if ("READ".equals(query.status())) {
                stream = stream.filter(item -> !item.unread());
            }
        }
        return stream.sorted(Comparator
                .comparing(UserNotification::createdAt)
                .reversed()
                .thenComparing(UserNotification::id));
    }
}
