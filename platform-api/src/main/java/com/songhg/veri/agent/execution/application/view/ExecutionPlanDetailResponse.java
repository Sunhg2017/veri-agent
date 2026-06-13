package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ExecutionPlanDetailResponse(
        @Schema(description = "Plan ID")
        UUID id,
        String projectId,
        String name,
        String status,
        String environmentKey,
        String description,
        String dagDigest,
        Map<String, Object> triggerPolicy,
        List<ExecutionPlanNodeResponse> nodes,
        String createdBy,
        String updatedBy,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
