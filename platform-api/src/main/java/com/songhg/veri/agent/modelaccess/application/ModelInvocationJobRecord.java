package com.songhg.veri.agent.modelaccess.application;

import java.time.Instant;
import java.util.UUID;

public record ModelInvocationJobRecord(
        UUID jobId,
        ModelInvocationJobStatus status,
        String requestJson,
        String actorService,
        String delegatedUserId,
        String traceId,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        UUID invocationId,
        String errorCode,
        String errorMessage,
        String responseJson
) {
}
