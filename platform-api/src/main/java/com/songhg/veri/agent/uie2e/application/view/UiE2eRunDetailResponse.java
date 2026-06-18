package com.songhg.veri.agent.uie2e.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UiE2eRunDetailResponse(
        UUID id,
        String projectId,
        UUID sceneId,
        String sceneCode,
        String sceneName,
        String sceneStatus,
        UUID bundleId,
        String bundleStatus,
        String status,
        String requestKey,
        String runnerMode,
        String failureCode,
        String failureSummary,
        String traceId,
        Map<String, Object> accountSummary,
        Map<String, Object> executionSummary,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt,
        @Schema(description = "Whether this response came from requestKey replay")
        boolean idempotentReplay
) {
}
