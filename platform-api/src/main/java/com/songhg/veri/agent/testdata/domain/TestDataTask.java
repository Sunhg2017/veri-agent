package com.songhg.veri.agent.testdata.domain;

import java.time.Instant;
import java.util.UUID;

public record TestDataTask(
        UUID id,
        String projectId,
        UUID dataSetId,
        String taskType,
        String status,
        String requestKey,
        String targetRef,
        int attempt,
        String resultSummaryJson,
        String errorCode,
        String errorSummary,
        String traceId,
        String createdBy,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
