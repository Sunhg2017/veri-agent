package com.songhg.veri.agent.modelaccess.application.view;

import java.time.Instant;
import java.util.UUID;

/**
 * Application-layer view of an asynchronous invocation job.
 */
public record ModelInvocationJobResult(
        UUID jobId,
        ModelInvocationJobStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        UUID invocationId,
        String errorCode,
        String errorMessage,
        String traceId,
        ModelInvocationResult response
) {
}
