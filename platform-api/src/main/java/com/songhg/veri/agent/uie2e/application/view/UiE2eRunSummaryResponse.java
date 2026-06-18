package com.songhg.veri.agent.uie2e.application.view;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UiE2eRunSummaryResponse(
        UUID id,
        String projectId,
        UUID sceneId,
        String sceneCode,
        String sceneName,
        UUID bundleId,
        String status,
        String requestKey,
        String runnerMode,
        String failureCode,
        String failureSummary,
        String traceId,
        Map<String, Object> accountSummary,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
