package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ExecutionNodeRunResponse(
        @Schema(description = "Node run ID")
        UUID id,
        @Schema(description = "Source plan node ID")
        UUID planNodeId,
        @Schema(description = "Source plan node key")
        String nodeKey,
        @Schema(description = "Source plan node type")
        String nodeType,
        @Schema(description = "Node run lifecycle status")
        String status,
        @Schema(description = "Node run attempt")
        int attempt,
        @Schema(description = "Runner integration type")
        String runnerType,
        @Schema(description = "External run ID, if dispatched")
        String externalRunId,
        @Schema(description = "Sanitized error code")
        String errorCode,
        @Schema(description = "Sanitized error summary")
        String errorSummary,
        @Schema(description = "Sanitized node result summary")
        Map<String, Object> resultSummary,
        Instant heartbeatAt,
        Instant queuedAt,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
