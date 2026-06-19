package com.songhg.veri.agent.uie2e.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UiE2eBundleExportBundleResponse(
        UUID id,
        String projectId,
        UUID sceneId,
        String sceneCode,
        String sceneName,
        String sceneStatus,
        String applicationId,
        String environmentId,
        String riskLevel,
        List<String> tags,
        String status,
        String bundleDigest,
        String staticCheckStatus,
        Map<String, Object> specSummary,
        Map<String, Object> fixtureSummary,
        Map<String, Object> staticCheckSummary,
        Map<String, Object> policy,
        Instant submittedAt,
        Instant approvedAt,
        Instant rejectedAt,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
