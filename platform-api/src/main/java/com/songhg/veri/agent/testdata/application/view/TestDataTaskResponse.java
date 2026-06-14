package com.songhg.veri.agent.testdata.application.view;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TestDataTaskResponse(
        UUID id,
        String projectId,
        UUID dataSetId,
        String taskType,
        String status,
        String requestKey,
        String targetRef,
        int attempt,
        Map<String, Object> resultSummary,
        String errorCode,
        String errorSummary,
        String traceId,
        Map<String, Object> policy,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
