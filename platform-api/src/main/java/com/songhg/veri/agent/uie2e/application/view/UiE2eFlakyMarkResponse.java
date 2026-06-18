package com.songhg.veri.agent.uie2e.application.view;

import java.time.Instant;
import java.util.UUID;

public record UiE2eFlakyMarkResponse(
        UUID id,
        String projectId,
        UUID sceneId,
        String sceneCode,
        String sceneName,
        UUID runId,
        String runStatus,
        String status,
        String reasonCode,
        String reasonSummary,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
