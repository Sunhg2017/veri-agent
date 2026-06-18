package com.songhg.veri.agent.notification.application;

import com.songhg.veri.agent.notification.application.view.UserNotificationResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Maintains per-user SSE subscribers for in-app notifications.
 *
 * <p>The stream is intentionally ephemeral and in-memory: notifications remain durable in the repository,
 * while this service only fans out low-latency UI updates and heartbeats for currently connected sessions.</p>
 */
@Service
public class NotificationStreamService {

    private final ConcurrentMap<UUID, ConcurrentMap<String, SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final AtomicLong subscriberSequence = new AtomicLong(1);
    private final long streamTimeoutMs;

    public NotificationStreamService(
            @Value("${veri-agent.notification.stream-timeout-ms:1800000}") long streamTimeoutMs
    ) {
        this.streamTimeoutMs = streamTimeoutMs;
    }

    public SseEmitter subscribe(UUID userId, long unreadCount) {
        String subscriberId = "sub-" + subscriberSequence.getAndIncrement();
        SseEmitter emitter = new SseEmitter(streamTimeoutMs);
        subscribers.computeIfAbsent(userId, key -> new ConcurrentHashMap<>()).put(subscriberId, emitter);
        emitter.onCompletion(() -> unregister(userId, subscriberId));
        emitter.onTimeout(() -> {
            unregister(userId, subscriberId);
            emitter.complete();
        });
        emitter.onError(error -> unregister(userId, subscriberId));
        sendToEmitter(userId, subscriberId, emitter, "connected", Map.of(
                "timestamp", Instant.now(),
                "unreadCount", unreadCount
        ));
        sendToEmitter(userId, subscriberId, emitter, "unread-count", Map.of("unreadCount", unreadCount));
        return emitter;
    }

    /**
     * Completes active emitters for the user. Production code currently relies on timeout and client disconnect,
     * while tests use this hook to close the async response deterministically.
     */
    public void completeUserStreams(UUID userId) {
        ConcurrentMap<String, SseEmitter> emitters = subscribers.remove(userId);
        if (emitters == null) {
            return;
        }
        emitters.values().forEach(SseEmitter::complete);
    }

    public void publishCreated(UUID userId, UserNotificationResponse notification, long unreadCount) {
        sendToUser(userId, "notification-created", Map.of(
                "notification", notification,
                "unreadCount", unreadCount
        ));
        publishUnreadCount(userId, unreadCount);
    }

    public void publishRead(UUID userId, UserNotificationResponse notification, long unreadCount) {
        sendToUser(userId, "notification-read", Map.of(
                "notification", notification,
                "unreadCount", unreadCount
        ));
        publishUnreadCount(userId, unreadCount);
    }

    public void publishReadAll(UUID userId, Instant readAt, long unreadCount) {
        sendToUser(userId, "notification-read-all", Map.of(
                "readAt", readAt,
                "unreadCount", unreadCount
        ));
        publishUnreadCount(userId, unreadCount);
    }

    public void publishUnreadCount(UUID userId, long unreadCount) {
        sendToUser(userId, "unread-count", Map.of("unreadCount", unreadCount));
    }

    public void heartbeat() {
        Instant now = Instant.now();
        subscribers.forEach((userId, emitters) -> emitters.forEach((subscriberId, emitter) ->
                sendToEmitter(userId, subscriberId, emitter, "heartbeat", Map.of("timestamp", now))
        ));
    }

    private void sendToUser(UUID userId, String eventName, Object payload) {
        ConcurrentMap<String, SseEmitter> emitters = subscribers.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.forEach((subscriberId, emitter) -> sendToEmitter(userId, subscriberId, emitter, eventName, payload));
    }

    private void sendToEmitter(
            UUID userId,
            String subscriberId,
            SseEmitter emitter,
            String eventName,
            Object payload
    ) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException exception) {
            unregister(userId, subscriberId);
            emitter.completeWithError(exception);
        }
    }

    private void unregister(UUID userId, String subscriberId) {
        subscribers.computeIfPresent(userId, (key, emitters) -> {
            emitters.remove(subscriberId);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
