package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record AssetBusinessFlow(
        UUID id,
        String code,
        String name,
        String description,
        String flowJson,
        String priority,
        String projectId,
        String status,
        String lifecycleStatus,
        Instant archivedAt,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
