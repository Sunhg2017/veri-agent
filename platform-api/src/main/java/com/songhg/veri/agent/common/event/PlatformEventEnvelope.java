package com.songhg.veri.agent.common.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.time.Instant;
import java.util.UUID;

public record PlatformEventEnvelope(
        String eventId,
        String eventType,
        String aggregateId,
        String traceId,
        Instant occurredAt,
        JsonNode payload
) {

    /**
     * Creates the canonical event envelope used by local and Kafka transports.
     */
    public static PlatformEventEnvelope of(
            String eventType,
            String aggregateId,
            Object payload,
            ObjectMapper objectMapper
    ) {
        return new PlatformEventEnvelope(
                UUID.randomUUID().toString(),
                eventType,
                aggregateId,
                TraceContext.getOrCreateTraceId(),
                Instant.now(),
                objectMapper.valueToTree(payload)
        );
    }

    public <T> T payloadAs(ObjectMapper objectMapper, Class<T> payloadType) {
        try {
            return objectMapper.treeToValue(payload, payloadType);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "事件载荷无法反序列化: " + eventType);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "事件载荷无法反序列化: " + eventType);
        }
    }
}
