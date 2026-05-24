package com.songhg.veri.agent.asset.application;

import java.time.Instant;
import java.util.UUID;

public record RequirementResponse(
        UUID id,
        String code,
        String title,
        String description,
        String source,
        String sourceRef,
        String sourceUrl,
        String acceptanceCriteria,
        String status,
        String priority,
        String projectId,
        String tags,
        int version,
        String lifecycleStatus,
        Instant archivedAt,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
