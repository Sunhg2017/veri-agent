package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record ExecutionQueueRecoveryResponse(
        @Schema(description = "Recovered expired claim count")
        int expiredClaimCount,
        @Schema(description = "Node run count re-queued for another claim")
        int requeuedNodeCount,
        @Schema(description = "Node run count marked as timeout")
        int timedOutNodeCount,
        @Schema(description = "Run count re-aggregated after recovery")
        int aggregatedRunCount,
        @Schema(description = "Recovery scan timestamp")
        Instant recoveredAt
) {
}
