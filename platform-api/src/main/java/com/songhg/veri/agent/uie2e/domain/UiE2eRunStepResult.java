package com.songhg.veri.agent.uie2e.domain;

import java.time.Instant;
import java.util.UUID;

public record UiE2eRunStepResult(
        UUID id,
        UUID runId,
        UUID sceneStepId,
        int stepOrder,
        String status,
        int durationMs,
        String failureBucket,
        String errorCode,
        String summaryJson,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
