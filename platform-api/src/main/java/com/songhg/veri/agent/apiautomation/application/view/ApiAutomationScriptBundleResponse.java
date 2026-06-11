package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ApiAutomationScriptBundleResponse(
        @Schema(description = "脚本包 ID")
        UUID id,
        String projectId,
        UUID taskId,
        String status,
        String bundleDigest,
        int fileCount,
        Map<String, Object> fileTreeSummary,
        Map<String, Object> dependencySummary,
        String staticCheckStatus,
        Map<String, Object> staticCheckSummary,
        String reviewNote,
        String submittedBy,
        String approvedBy,
        Instant submittedAt,
        Instant approvedAt,
        Instant rejectedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
