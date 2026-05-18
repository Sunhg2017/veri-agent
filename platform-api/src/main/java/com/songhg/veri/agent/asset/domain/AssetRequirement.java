package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record AssetRequirement(
        UUID id,
        String title,
        String description,
        String status,
        String priority,
        String projectId,
        String tags,
        Instant createdAt,
        Instant updatedAt
) {
}
