package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ExecutionRunDetailResponse(
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
        @Schema(description = "External source event ID")
        String sourceEventId,
        @Schema(description = "Run attempt")
        int attempt,
        @Schema(description = "Trace ID")
        String traceId,
        @Schema(description = "Sanitized run result summary")
        Map<String, Object> resultSummary,
        @Schema(description = "Sanitized error code")
        String errorCode,
        @Schema(description = "Sanitized error summary")
        String errorSummary,
        @Schema(description = "Node runs")
        List<ExecutionNodeRunResponse> nodes,
        @Schema(description = "Whether this response came from requestKey replay")
        boolean idempotentReplay,
        String createdBy,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
