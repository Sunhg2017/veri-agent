package com.songhg.veri.agent.reporting.domain;

import java.time.Instant;
import java.util.UUID;

public record ReportExecutionReport(
        UUID id,
        String projectId,
        UUID executionRunId,
        String requestKey,
        String status,
        String schemaVersion,
        String sourceRunDigest,
        String reportSummaryJson,
        String redactionPolicyJson,
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
