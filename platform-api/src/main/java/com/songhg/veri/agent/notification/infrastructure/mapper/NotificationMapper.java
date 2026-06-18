package com.songhg.veri.agent.notification.infrastructure.mapper;

import com.songhg.veri.agent.notification.application.query.NotificationQuery;
import com.songhg.veri.agent.notification.domain.UserNotification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationMapper {

    void insert(UserNotification notification);

    List<UserNotification> notifications(
            @Param("userId") UUID userId,
            @Param("query") NotificationQuery query
    );

    long countNotifications(
            @Param("userId") UUID userId,
            @Param("query") NotificationQuery query
    );

    long countUnread(@Param("userId") UUID userId);

    UserNotification notification(@Param("id") UUID id);

    int markRead(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("readAt") Instant readAt
    );

    int markAllRead(
            @Param("userId") UUID userId,
            @Param("readAt") Instant readAt
    );
}
