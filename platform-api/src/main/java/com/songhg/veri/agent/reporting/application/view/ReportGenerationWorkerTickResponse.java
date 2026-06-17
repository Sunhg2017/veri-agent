package com.songhg.veri.agent.reporting.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record ReportGenerationWorkerTickResponse(
        @Schema(description = "Whether the managed report generation worker is enabled")
        boolean workerEnabled,
        @Schema(description = "Worker ID used for this tick")
        String workerId,
        @Schema(description = "Maximum queued reports scanned by this tick")
        int batchSize,
        @Schema(description = "Stale GENERATING reports recovered to FAILED")
        int recoveredStaleCount,
        @Schema(description = "Queued reports successfully claimed")
        int claimedReportCount,
        @Schema(description = "Queued reports completed as READY")
        int readyReportCount,
        @Schema(description = "Queued reports completed as FAILED")
        int failedReportCount,
        @Schema(description = "Queued candidates skipped because another worker claimed them first")
        int skippedCandidateCount,
        @Schema(description = "Whether this tick had no useful work")
        boolean noop,
        @Schema(description = "Trace ID opened for the worker tick")
        String traceId,
        @Schema(description = "Tick timestamp")
        Instant tickedAt
) {
}
