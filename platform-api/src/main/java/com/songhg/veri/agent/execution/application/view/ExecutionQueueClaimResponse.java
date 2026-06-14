package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ExecutionQueueClaimResponse(
        @Schema(description = "Claim ID")
        UUID id,
        @Schema(description = "Run ID")
        UUID runId,
        @Schema(description = "Node run ID")
        UUID nodeRunId,
        @Schema(description = "Source plan node ID")
        UUID planNodeId,
        @Schema(description = "Source plan node key")
        String nodeKey,
        @Schema(description = "Runner integration type")
        String runnerType,
        @Schema(description = "Opaque claim token")
        String claimToken,
        @Schema(description = "Worker ID")
        String workerId,
        Instant claimedAt,
        Instant heartbeatAt,
        Instant expiresAt
) {
}
