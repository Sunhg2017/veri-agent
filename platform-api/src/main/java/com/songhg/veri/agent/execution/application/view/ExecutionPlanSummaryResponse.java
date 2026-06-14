package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ExecutionPlanSummaryResponse(
        @Schema(description = "Plan ID")
        UUID id,
        String projectId,
        String name,
        String status,
        String environmentKey,
        String description,
        String dagDigest,
        int nodeCount,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
