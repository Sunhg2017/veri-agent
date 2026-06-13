package com.songhg.veri.agent.execution.domain;

import java.time.Instant;
import java.util.UUID;

public record ExecutionTriggerEvent(
        UUID id,
        UUID triggerId,
        String sourceEventId,
        String requestDigest,
        String status,
        UUID runId,
        Instant receivedAt,
        String errorCode,
        String errorSummary,
        String traceId
) {
}
