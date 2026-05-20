package com.songhg.veri.agent.asset.api.response;

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
        Instant createdAt,
        Instant updatedAt
) {
}
