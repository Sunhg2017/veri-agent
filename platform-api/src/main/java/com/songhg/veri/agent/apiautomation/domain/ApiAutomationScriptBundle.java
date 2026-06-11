package com.songhg.veri.agent.apiautomation.domain;

import java.time.Instant;
import java.util.UUID;

public record ApiAutomationScriptBundle(
        UUID id,
        String projectId,
        UUID taskId,
        String status,
        String bundleDigest,
        int fileCount,
        String fileTreeSummaryJson,
        String dependencySummaryJson,
        String staticCheckStatus,
        String staticCheckSummaryJson,
        String reviewNote,
        String submittedBy,
        String approvedBy,
        Instant submittedAt,
        Instant approvedAt,
        Instant rejectedAt,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
