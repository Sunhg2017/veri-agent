package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.execution.application.view.ExecutionTriggerEventResponse;
import com.songhg.veri.agent.execution.domain.ExecutionTriggerEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * Builds sanitized WP9 trigger-event state transitions without persisting raw webhook or cron payloads.
 */
final class ExecutionTriggerEventSupport {

    ExecutionTriggerEvent receivedEvent(UUID triggerId, String sourceEventId, String requestDigest, Instant now) {
        return new ExecutionTriggerEvent(
                UUID.randomUUID(),
                triggerId,
                sourceEventId,
                requestDigest,
                "RECEIVED",
                null,
                now,
                null,
                null,
                TraceContext.getOrCreateTraceId()
        );
    }

    ExecutionTriggerEvent duplicateEvent(ExecutionTriggerEvent existing, String requestDigest, Instant now) {
        if (!Objects.equals(existing.requestDigest(), requestDigest)) {
            return new ExecutionTriggerEvent(
                    existing.id(),
                    existing.triggerId(),
                    existing.sourceEventId(),
                    existing.requestDigest(),
                    "DUPLICATE",
                    existing.runId(),
                    existing.receivedAt(),
                    "EXECUTION_TRIGGER_DUPLICATE_PAYLOAD",
                    "Duplicate sourceEventId received with different request digest",
                    TraceContext.getOrCreateTraceId()
            );
        }
        return new ExecutionTriggerEvent(
                existing.id(),
                existing.triggerId(),
                existing.sourceEventId(),
                existing.requestDigest(),
                "DUPLICATE",
                existing.runId(),
                existing.receivedAt() == null ? now : existing.receivedAt(),
                existing.errorCode(),
                existing.errorSummary(),
                TraceContext.getOrCreateTraceId()
        );
    }

    ExecutionTriggerEvent retryReceivedEvent(
            ExecutionTriggerEvent existing,
            String requestDigest,
            Instant now
    ) {
        return new ExecutionTriggerEvent(
                existing.id(),
                existing.triggerId(),
                existing.sourceEventId(),
                requestDigest,
                "RECEIVED",
                null,
                now,
                null,
                null,
                TraceContext.getOrCreateTraceId()
        );
    }

    ExecutionTriggerEvent acceptedEvent(ExecutionTriggerEvent event, UUID runId) {
        return new ExecutionTriggerEvent(
                event.id(),
                event.triggerId(),
                event.sourceEventId(),
                event.requestDigest(),
                "ACCEPTED",
                runId,
                event.receivedAt(),
                null,
                null,
                event.traceId()
        );
    }

    ExecutionTriggerEvent failedEvent(ExecutionTriggerEvent event, String errorCode, String errorSummary) {
        return new ExecutionTriggerEvent(
                event.id(),
                event.triggerId(),
                event.sourceEventId(),
                event.requestDigest(),
                "FAILED",
                event.runId(),
                event.receivedAt(),
                boundedNullableText(errorCode, 64),
                boundedNullableText(errorSummary, 512),
                event.traceId()
        );
    }

    ExecutionTriggerEvent rejectedEvent(ExecutionTriggerEvent event, String errorCode, String errorSummary) {
        return new ExecutionTriggerEvent(
                event.id(),
                event.triggerId(),
                event.sourceEventId(),
                event.requestDigest(),
                "REJECTED",
                event.runId(),
                event.receivedAt(),
                boundedNullableText(errorCode, 64),
                boundedNullableText(errorSummary, 512),
                event.traceId()
        );
    }

    ExecutionTriggerEventResponse toEventResponse(ExecutionTriggerEvent event) {
        return new ExecutionTriggerEventResponse(
                event.id(),
                event.triggerId(),
                event.sourceEventId(),
                event.requestDigest(),
                event.status(),
                event.runId(),
                event.receivedAt(),
                event.errorCode(),
                event.errorSummary(),
                event.traceId()
        );
    }

    String cronErrorCode(RuntimeException exception) {
        if (StringUtils.hasText(exception.getMessage()) && exception.getMessage().matches("[A-Z0-9_.:-]{1,64}")) {
            return exception.getMessage();
        }
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().name();
        }
        return "EXECUTION_CRON_TRIGGER_FAILED";
    }

    String sanitizedTriggerErrorSummary(String value) {
        String bounded = boundedNullableText(value, 512);
        return StringUtils.hasText(bounded) ? bounded : "Cron trigger failed";
    }

    String boundedRequiredText(String value, int maxLength, String errorCode) {
        String bounded = boundedNullableText(value, maxLength);
        if (!StringUtils.hasText(bounded)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorCode);
        }
        return bounded;
    }

    private String boundedNullableText(String value, int maxLength) {
        return SensitiveTextSanitizer.boundedNullableText(value, maxLength);
    }
}
