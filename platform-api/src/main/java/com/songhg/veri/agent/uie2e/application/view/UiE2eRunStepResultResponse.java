package com.songhg.veri.agent.uie2e.application.view;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UiE2eRunStepResultResponse(
        UUID id,
        UUID sceneStepId,
        int stepOrder,
        String status,
        int durationMs,
        String failureBucket,
        String errorCode,
        Map<String, Object> summary,
        Instant createdAt,
        Instant updatedAt
) {
}
