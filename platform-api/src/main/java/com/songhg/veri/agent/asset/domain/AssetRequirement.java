package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record AssetRequirement(
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
        Instant createdAt,
        Instant updatedAt
) {
}
