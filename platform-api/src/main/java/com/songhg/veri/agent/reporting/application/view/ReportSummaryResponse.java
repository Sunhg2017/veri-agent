package com.songhg.veri.agent.reporting.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ReportSummaryResponse(
        @Schema(description = "Report ID")
        UUID id,
        @Schema(description = "Owning project scope ID")
        String projectId,
        @Schema(description = "Source WP9 execution run ID")
        UUID executionRunId,
        @Schema(description = "Report generation idempotency key")
        String requestKey,
        @Schema(description = "Report lifecycle status")
        String status,
        @Schema(description = "Report schema version")
        String schemaVersion,
        @Schema(description = "SHA-256 digest of sanitized source run export")
        String sourceRunDigest,
        @Schema(description = "Aggregate-only report summary")
        Map<String, Object> summary,
        @Schema(description = "Whether this response came from requestKey replay")
        boolean idempotentReplay,
        String generatedBy,
        Instant generatedAt,
        String failedCode,
        String failureSummary,
        String traceId,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
