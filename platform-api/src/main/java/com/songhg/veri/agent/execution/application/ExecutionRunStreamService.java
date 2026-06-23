package com.songhg.veri.agent.execution.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.view.ExecutionNodeRunResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunLogEntryResponse;
import com.songhg.veri.agent.execution.domain.ExecutionRunLogEntry;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Maintains in-memory SSE subscribers for one WP9 execution run so the workbench can receive low-latency status
 * snapshots and sanitized control-plane log lines without persisting raw runner output.
 */
@Service
public class ExecutionRunStreamService implements ExecutionRunEventPublisher {

    private static final Set<String> FORBIDDEN_METADATA_KEY_PARTS = Set.of(
            "secret", "secrets", "token", "password", "authorization", "stdout", "stderr",
            "request", "requestbody", "response", "responsebody", "body", "variables", "cookie", "setcookie"
    );
    private static final Set<String> ALLOWED_LEVELS = Set.of("INFO", "WARN", "ERROR", "SUCCESS");
    private static final int MAX_TEXT_LENGTH = 512;
    private static final int MAX_LIST_ITEMS = 12;
    private static final int MAX_MAP_ENTRIES = 16;

    private final ConcurrentMap<UUID, ConcurrentMap<String, SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final AtomicLong subscriberSequence = new AtomicLong(1);
    private final long streamTimeoutMs;
    private final ExecutionRepository repository;
    private final ExecutionRunJsonSupport jsonSupport;
    private final ExecutionRunLogHistoryService logHistoryService;
    private final int replayLimit;

    public ExecutionRunStreamService(
            ExecutionRepository repository,
            ObjectMapper objectMapper,
            @Value("${veri-agent.execution.run-stream-timeout-ms:1800000}") long streamTimeoutMs,
            @Value("${veri-agent.execution.run-stream-replay-limit:50}") int replayLimit
    ) {
        this.repository = repository;
        this.jsonSupport = new ExecutionRunJsonSupport(objectMapper);
        this.logHistoryService = new ExecutionRunLogHistoryService(repository, this.jsonSupport);
        this.streamTimeoutMs = streamTimeoutMs;
        this.replayLimit = replayLimit;
    }

    public SseEmitter subscribe(UUID runId, ExecutionRunDetailResponse initialSnapshot) {
        String subscriberId = "run-sub-" + subscriberSequence.getAndIncrement();
        SseEmitter emitter = new SseEmitter(streamTimeoutMs);
        subscribers.computeIfAbsent(runId, key -> new ConcurrentHashMap<>()).put(subscriberId, emitter);
        emitter.onCompletion(() -> unregister(runId, subscriberId));
        emitter.onTimeout(() -> {
            unregister(runId, subscriberId);
            emitter.complete();
        });
        emitter.onError(error -> unregister(runId, subscriberId));
        sendToEmitter(runId, subscriberId, emitter, "connected", Map.of(
                "runId", runId.toString(),
                "status", boundedText(initialSnapshot.status(), 32),
                "timestamp", Instant.now().toString()
        ));
        sendToEmitter(runId, subscriberId, emitter, "snapshot", Map.of("run", initialSnapshot));
        sendHistoryToEmitter(runId, subscriberId, emitter);
        return emitter;
    }

    @Override
    public void publish(
            ExecutionRunDetailResponse run,
            String level,
            String stage,
            String message,
            UUID nodeRunId,
            Map<String, Object> metadata
    ) {
        if (run == null) {
            return;
        }
        ExecutionRunLogEntry entry = logEntry(run, level, stage, message, nodeRunId, metadata);
        repository.insertRunLog(entry);
        publishAfterCommit(() -> {
            sendToRun(run.id(), "snapshot", Map.of("run", run));
            sendToRun(run.id(), "log", logPayload(run, entry));
        });
    }

    public void heartbeat() {
        Instant now = Instant.now();
        subscribers.forEach((runId, emitters) -> emitters.forEach((subscriberId, emitter) ->
                sendToEmitter(runId, subscriberId, emitter, "heartbeat", Map.of("timestamp", now.toString()))
        ));
    }

    /**
     * Production code relies on client disconnect or timeout. Tests use this hook to close async responses
     * deterministically after asserting emitted events.
     */
    public void completeRunStreams(UUID runId) {
        ConcurrentMap<String, SseEmitter> emitters = subscribers.remove(runId);
        if (emitters == null) {
            return;
        }
        emitters.values().forEach(SseEmitter::complete);
    }

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

    private Map<String, Object> logPayload(
            ExecutionRunDetailResponse run,
            ExecutionRunLogEntry entry
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", run.id().toString());
        payload.put("status", boundedText(run.status(), 32));
        payload.put("logId", entry.id().toString());
        payload.put("level", safeLevel(entry.level()));
        payload.put("stage", boundedText(entry.stage(), 64));
        payload.put("message", entry.message());
        payload.put("timestamp", entry.eventAt() == null ? Instant.now().toString() : entry.eventAt().toString());
        if (entry.nodeRunId() != null) {
            payload.put("nodeRunId", entry.nodeRunId().toString());
            String nodeKey = nodeKey(run, entry.nodeRunId());
            if (StringUtils.hasText(nodeKey)) {
                payload.put("nodeKey", nodeKey);
            }
        }
        payload.put("metadata", jsonSupport.readMap(entry.metadataJson()));
        return payload;
    }

    private ExecutionRunLogEntry logEntry(
            ExecutionRunDetailResponse run,
            String level,
            String stage,
            String message,
            UUID nodeRunId,
            Map<String, Object> metadata
    ) {
        Instant now = Instant.now();
        return new ExecutionRunLogEntry(
                UUID.randomUUID(),
                run.id(),
                nodeRunId,
                safeLevel(level),
                boundedNullable(stage, 64),
                SensitiveTextSanitizer.sanitizedEvidenceText(message, MAX_TEXT_LENGTH),
                jsonSupport.json(sanitizeMetadata(metadata == null ? Map.of() : metadata)),
                now,
                now
        );
    }

    private Object sanitizeMetadata(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String text) {
            return SensitiveTextSanitizer.sanitizedEvidenceText(text, MAX_TEXT_LENGTH);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count >= MAX_MAP_ENTRIES) {
                    sanitized.put("truncated", true);
                    break;
                }
                String key = entry.getKey() == null ? null : String.valueOf(entry.getKey());
                if (!StringUtils.hasText(key) || forbiddenMetadataKey(key)) {
                    continue;
                }
                sanitized.put(key, sanitizeMetadata(entry.getValue()));
                count++;
            }
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            int count = 0;
            for (Object item : iterable) {
                if (count >= MAX_LIST_ITEMS) {
                    sanitized.add("...");
                    break;
                }
                sanitized.add(sanitizeMetadata(item));
                count++;
            }
            return sanitized;
        }
        return SensitiveTextSanitizer.sanitizedEvidenceText(String.valueOf(value), MAX_TEXT_LENGTH);
    }

    private boolean forbiddenMetadataKey(String key) {
        String normalized = key.replace("_", "").replace("-", "").toLowerCase();
        return FORBIDDEN_METADATA_KEY_PARTS.stream().anyMatch(normalized::contains);
    }

    private String safeLevel(String value) {
        String normalized = boundedText(value, 16).toUpperCase(java.util.Locale.ROOT);
        return ALLOWED_LEVELS.contains(normalized) ? normalized : "INFO";
    }

    private String nodeKey(ExecutionRunDetailResponse run, UUID nodeRunId) {
        return run.nodes().stream()
                .filter(node -> nodeRunId.equals(node.id()))
                .map(ExecutionNodeRunResponse::nodeKey)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String boundedText(String value, int maxLength) {
        String normalized = SensitiveTextSanitizer.boundedNullableText(value, maxLength);
        return normalized == null ? "" : normalized;
    }

    private String boundedNullable(String value, int maxLength) {
        return SensitiveTextSanitizer.boundedNullableText(value, maxLength);
    }

    private void sendHistoryToEmitter(UUID runId, String subscriberId, SseEmitter emitter) {
        List<ExecutionRunLogEntryResponse> history = logHistoryService.recentHistory(runId, replayLimit);
        for (int index = history.size() - 1; index >= 0; index--) {
            ExecutionRunLogEntryResponse entry = history.get(index);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("runId", entry.runId().toString());
            payload.put("status", "");
            payload.put("logId", entry.id().toString());
            payload.put("level", entry.level());
            payload.put("stage", entry.stage());
            payload.put("message", entry.message());
            payload.put("timestamp", entry.eventAt() == null ? null : entry.eventAt().toString());
            if (entry.nodeRunId() != null) {
                payload.put("nodeRunId", entry.nodeRunId().toString());
            }
            if (StringUtils.hasText(entry.nodeKey())) {
                payload.put("nodeKey", entry.nodeKey());
            }
            payload.put("metadata", entry.metadata());
            payload.put("historyReplay", true);
            sendToEmitter(runId, subscriberId, emitter, "log", payload);
        }
    }

    private void sendToRun(UUID runId, String eventName, Object payload) {
        ConcurrentMap<String, SseEmitter> emitters = subscribers.get(runId);
        if (emitters == null) {
            return;
        }
        emitters.forEach((subscriberId, emitter) -> sendToEmitter(runId, subscriberId, emitter, eventName, payload));
    }

    private void sendToEmitter(
            UUID runId,
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
            unregister(runId, subscriberId);
            emitter.completeWithError(exception);
        }
    }

    private void unregister(UUID runId, String subscriberId) {
        subscribers.computeIfPresent(runId, (key, emitters) -> {
            emitters.remove(subscriberId);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
