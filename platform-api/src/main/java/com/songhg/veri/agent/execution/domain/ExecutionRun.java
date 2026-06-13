package com.songhg.veri.agent.execution.domain;

import java.time.Instant;
import java.util.UUID;

public record ExecutionRun(
        UUID id,
        UUID planId,
        String projectId,
        String status,
        String triggerType,
        String requestKey,
        String sourceEventId,
        int attempt,
        String traceId,
        String resultSummaryJson,
        String errorCode,
        String errorSummary,
        String createdBy,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
