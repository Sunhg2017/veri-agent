package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ApiAutomationRunResponse(
        @Schema(description = "运行任务 ID")
        UUID id,
        String projectId,
        UUID bundleId,
        String environmentId,
        String baseUrlDigest,
        String baseUrlHost,
        String status,
        int timeoutSeconds,
        int caseCount,
        String traceId,
        String runnerMode,
        String errorCode,
        String errorSummary,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
