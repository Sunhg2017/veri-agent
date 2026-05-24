package com.songhg.veri.agent.modelaccess.api.response;

import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobStatus;
import java.time.Instant;
import java.util.UUID;

public record ModelInvocationJobResponse(
        UUID jobId,
        ModelInvocationJobStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        UUID invocationId,
        String errorCode,
        String errorMessage,
        String traceId,
        InvokeModelResponse response
) {
}
