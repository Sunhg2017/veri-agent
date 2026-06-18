package com.songhg.veri.agent.notification.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.notification.application.port.UserNotificationRepository;
import com.songhg.veri.agent.notification.application.query.NotificationListRequest;
import com.songhg.veri.agent.notification.application.query.NotificationQuery;
import com.songhg.veri.agent.notification.application.view.NotificationBatchReadResponse;
import com.songhg.veri.agent.notification.application.view.UnreadNotificationCountResponse;
import com.songhg.veri.agent.notification.application.view.UserNotificationResponse;
import com.songhg.veri.agent.notification.domain.UserNotification;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class UserNotificationService implements NotificationPublisher {

    private static final Set<String> NOTIFICATION_TYPES = Set.of(
            "ASYNC_TASK_COMPLETED",
            "ASYNC_TASK_FAILED",
            "REPORT_READY",
            "REPORT_FAILED",
            "SYSTEM_INFO"
    );
    private static final Set<String> QUERY_STATUSES = Set.of("UNREAD", "READ");

    private final UserNotificationRepository repository;
    private final ObjectMapper objectMapper;
    private final NotificationStreamService streamService;

    public UserNotificationService(
            UserNotificationRepository repository,
            ObjectMapper objectMapper,
            NotificationStreamService streamService
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.streamService = streamService;
    }

    @Transactional
    @Override
    public void publishToUser(
            UUID userId,
            String type,
            String title,
            String body,
            String link,
            Map<String, Object> metadata
    ) {
        if (userId == null) {
            return;
        }
        String normalizedType = normalizeType(type);
        String normalizedTitle = boundedRequired(title, 160, "通知标题不能为空");
        String normalizedBody = boundedRequired(body, 2000, "通知内容不能为空");
        String normalizedLink = boundedNullable(link, 512);
        Instant now = Instant.now();
        UserNotification notification = new UserNotification(
                UUID.randomUUID(),
                userId,
                normalizedType,
                normalizedTitle,
                normalizedBody,
                normalizedLink,
                json(metadata == null ? Map.of() : metadata),
                null,
                "system",
                now,
                now
        );
        UserNotification saved = repository.insert(notification);
        UserNotificationResponse response = response(saved);
        publishAfterCommit(() -> streamService.publishCreated(userId, response, repository.countUnread(userId)));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserNotificationResponse> notifications(
            AuthUserPrincipal principal,
            NotificationListRequest request
    ) {
        UUID userId = requireUserId(principal);
        NotificationQuery query = normalizeQuery(request.toQuery());
        List<UserNotificationResponse> items = repository.notifications(userId, query)
                .stream()
                .map(this::response)
                .toList();
        return PageResponse.of(items, query.index(), query.size(), repository.countNotifications(userId, query));
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse unreadCount(AuthUserPrincipal principal) {
        return new UnreadNotificationCountResponse(repository.countUnread(requireUserId(principal)));
    }

    @Transactional(readOnly = true)
    public SseEmitter stream(AuthUserPrincipal principal) {
        UUID userId = requireUserId(principal);
        return streamService.subscribe(userId, repository.countUnread(userId));
    }

    @Transactional
    public UserNotificationResponse markRead(AuthUserPrincipal principal, UUID notificationId) {
        UUID userId = requireUserId(principal);
        UserNotification current = repository.notification(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "通知不存在"));
        if (!userId.equals(current.userId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在");
        }
        if (current.unread()) {
            repository.markRead(notificationId, userId, Instant.now());
            current = repository.notification(notificationId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "通知不存在"));
        }
        UserNotificationResponse response = response(current);
        publishAfterCommit(() -> streamService.publishRead(userId, response, repository.countUnread(userId)));
        return response;
    }

    @Transactional
    public NotificationBatchReadResponse markAllRead(AuthUserPrincipal principal) {
        UUID userId = requireUserId(principal);
        Instant readAt = Instant.now();
        int marked = repository.markAllRead(userId, readAt);
        long unreadCount = repository.countUnread(userId);
        publishAfterCommit(() -> streamService.publishReadAll(userId, readAt, unreadCount));
        return new NotificationBatchReadResponse(marked, unreadCount);
    }

    /**
     * SSE subscribers only receive state that has been durably committed, so UI readers never observe
     * transient unread counts or notifications that might still roll back.
     */
    private void publishAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private NotificationQuery normalizeQuery(NotificationQuery query) {
        String status = normalizeNullableUpper(query.status(), QUERY_STATUSES, "通知状态不合法");
        return NotificationQuery.of(status, query.index(), query.size());
    }

    private UserNotificationResponse response(UserNotification notification) {
        return new UserNotificationResponse(
                notification.id(),
                notification.type(),
                notification.title(),
                notification.body(),
                notification.link(),
                readMap(notification.metadataJson()),
                notification.unread(),
                notification.readAt(),
                notification.createdAt(),
                notification.updatedAt()
        );
    }

    private UUID requireUserId(AuthUserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效");
        }
        return principal.userId();
    }

    private String normalizeType(String type) {
        String normalized = normalizeNullableUpper(type, NOTIFICATION_TYPES, "通知类型不合法");
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "通知类型不能为空");
        }
        return normalized;
    }

    private String normalizeNullableUpper(String value, Set<String> allowed, String message) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private String boundedRequired(String value, int maxLength, String emptyMessage) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, emptyMessage);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, emptyMessage + "，且长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String boundedNullable(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "通知链接超出长度限制");
        }
        return normalized;
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "通知元数据无法序列化");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            Object parsed = objectMapper.readValue(value, Map.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, item) -> normalized.put(String.valueOf(key), item));
                return Map.copyOf(normalized);
            }
            return Map.of();
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "通知元数据无法解析");
        }
    }
}
