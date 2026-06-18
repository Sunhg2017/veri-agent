package com.songhg.veri.agent.uie2e.domain;

import java.time.Instant;
import java.util.UUID;

public record UiE2eFlakyMark(
        UUID id,
        String projectId,
        UUID sceneId,
        UUID runId,
        String status,
        String reasonCode,
        String reasonSummary,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
