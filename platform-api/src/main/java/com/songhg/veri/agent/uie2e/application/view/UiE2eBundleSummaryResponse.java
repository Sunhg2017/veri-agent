package com.songhg.veri.agent.uie2e.application.view;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UiE2eBundleSummaryResponse(
        UUID id,
        String projectId,
        UUID sceneId,
        String sceneCode,
        String sceneName,
        String sceneStatus,
        String status,
        String bundleDigest,
        String staticCheckStatus,
        Map<String, Object> staticCheckSummary,
        Instant submittedAt,
        Instant approvedAt,
        Instant rejectedAt,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
