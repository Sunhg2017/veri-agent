package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record ExecutionCronScanResponse(
        @Schema(description = "Whether cron scanning is enabled")
        boolean scannerEnabled,
        @Schema(description = "Due cron triggers inspected by this scan")
        int scannedTriggerCount,
        @Schema(description = "Runs created or replayed by due cron triggers")
        int triggeredRunCount,
        @Schema(description = "Due cron triggers that failed validation or run creation")
        int failedTriggerCount,
        @Schema(description = "Scan timestamp")
        Instant scannedAt
) {
}
