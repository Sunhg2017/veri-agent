package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ApiAutomationRunResultResponse(
        @Schema(description = "用例级结果 ID")
        UUID id,
        UUID runId,
        UUID caseId,
        String status,
        int durationMs,
        Map<String, Object> assertionSummary,
        String errorCode,
        String errorSummary,
        Instant createdAt,
        Instant updatedAt
) {
}
