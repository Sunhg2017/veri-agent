package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record ExecutionSchedulerTickResponse(
        @Schema(description = "Whether the managed scheduler is enabled")
        boolean schedulerEnabled,
        @Schema(description = "Worker ID used by this tick")
        String workerId,
        @Schema(description = "Tick batch size after safety bounds")
        int tickBatchSize,
        @Schema(description = "Recovered expired claim count before claiming new work")
        int recoveredExpiredClaimCount,
        @Schema(description = "Nodes re-queued by recovery before claiming new work")
        int recoveredRequeuedNodeCount,
        @Schema(description = "Nodes timed out by recovery before claiming new work")
        int recoveredTimedOutNodeCount,
        @Schema(description = "Claims acquired by this tick")
        int claimedNodeCount,
        @Schema(description = "API_TEST nodes dispatched to WP6 by this tick")
        int dispatchedNodeCount,
        @Schema(description = "Non-runner nodes completed by this tick")
        int completedNodeCount,
        @Schema(description = "Claims closed as failed after dispatch or unsupported-runner errors")
        int failedNodeCount,
        @Schema(description = "True when recovery found no work and no node was claimed")
        boolean noop,
        @Schema(description = "Tick trace ID")
        String traceId,
        @Schema(description = "Tick timestamp")
        Instant tickedAt
) {
}
