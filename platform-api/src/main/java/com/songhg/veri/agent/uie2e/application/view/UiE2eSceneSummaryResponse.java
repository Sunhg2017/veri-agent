package com.songhg.veri.agent.uie2e.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UiE2eSceneSummaryResponse(
        UUID id,
        String projectId,
        String applicationId,
        String environmentId,
        String code,
        String name,
        String status,
        String riskLevel,
        List<String> tags,
        Map<String, Object> sourceSummary,
        int stepCount,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
