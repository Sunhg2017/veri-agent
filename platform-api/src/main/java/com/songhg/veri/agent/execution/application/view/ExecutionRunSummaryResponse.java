package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ExecutionRunSummaryResponse(
        @Schema(description = "Run ID")
        UUID id,
        @Schema(description = "Source plan ID")
        UUID planId,
        @Schema(description = "Project ID")
        String projectId,
        @Schema(description = "Run lifecycle status")
        String status,
        @Schema(description = "Trigger source")
        String triggerType,
        @Schema(description = "Manual trigger idempotency key")
        String requestKey,
        @Schema(description = "Run attempt")
        int attempt,
        @Schema(description = "Trace ID")
        String traceId,
        @Schema(description = "Sanitized run result summary")
        Map<String, Object> resultSummary,
        @Schema(description = "Node run count")
        int nodeCount,
        String createdBy,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
