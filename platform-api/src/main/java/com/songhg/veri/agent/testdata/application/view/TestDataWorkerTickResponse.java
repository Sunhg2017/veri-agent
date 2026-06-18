package com.songhg.veri.agent.testdata.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record TestDataWorkerTickResponse(
        @Schema(description = "Whether the managed WP8 worker is enabled")
        boolean workerEnabled,
        @Schema(description = "Worker ID used by this tick")
        String workerId,
        @Schema(description = "Maximum pending tasks scanned by this tick")
        int taskBatchSize,
        @Schema(description = "Maximum expired leases recovered by this tick")
        int leaseRecoveryBatchSize,
        @Schema(description = "Maximum pooled accounts checked by this tick")
        int accountHealthCheckBatchSize,
        @Schema(description = "Expired active leases recovered to EXPIRED")
        int recoveredExpiredLeaseCount,
        @Schema(description = "Pending tasks claimed by this tick")
        int claimedTaskCount,
        @Schema(description = "Tasks completed as SUCCEEDED")
        int succeededTaskCount,
        @Schema(description = "Tasks completed as FAILED")
        int failedTaskCount,
        @Schema(description = "Pending tasks skipped because another worker claimed them first")
        int skippedTaskCount,
        @Schema(description = "Pooled accounts scanned by the health checker")
        int scannedAccountCount,
        @Schema(description = "Pooled accounts updated by the health checker")
        int updatedAccountCount,
        @Schema(description = "Accounts moved into LOCKED by the health checker")
        int lockedAccountCount,
        @Schema(description = "Accounts reconciled back to LEASED by the health checker")
        int leasedAccountCount,
        @Schema(description = "Whether this tick found no useful work")
        boolean noop,
        @Schema(description = "Trace ID opened for the worker tick")
        String traceId,
        @Schema(description = "Tick timestamp")
        Instant tickedAt
) {
}
