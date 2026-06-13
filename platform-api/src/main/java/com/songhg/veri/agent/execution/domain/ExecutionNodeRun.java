package com.songhg.veri.agent.execution.domain;

import java.time.Instant;
import java.util.UUID;

public record ExecutionNodeRun(
        UUID id,
        UUID runId,
        UUID planNodeId,
        String status,
        int attempt,
        String runnerType,
        String externalRunId,
        String errorCode,
        String errorSummary,
        String resultSummaryJson,
        Instant heartbeatAt,
        Instant queuedAt,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
