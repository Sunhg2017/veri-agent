package com.songhg.veri.agent.uie2e.domain;

import java.time.Instant;
import java.util.UUID;

public record UiE2eScene(
        UUID id,
        String projectId,
        String applicationId,
        String environmentId,
        String code,
        String name,
        String status,
        String riskLevel,
        String sourceSummaryJson,
        String tagsJson,
        String createdBy,
        String updatedBy,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
