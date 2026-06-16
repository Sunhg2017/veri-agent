package com.songhg.veri.agent.reporting.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ReportDetailResponse(
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
        @Schema(description = "Aggregate-only redaction policy")
        Map<String, Object> redactionPolicy,
        @Schema(description = "Evidence manifests, filled by WP10 M3")
        List<ReportEvidenceManifestResponse> evidenceManifests,
        @Schema(description = "Latest diagnosis summary, filled by WP10 M4")
        Map<String, Object> latestDiagnosis,
        @Schema(description = "Defect draft summaries, filled by WP10 M5")
        List<Map<String, Object>> defectDrafts,
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
