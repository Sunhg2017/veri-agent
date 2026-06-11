package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ApiAutomationGenerationTaskResponse(
        @Schema(description = "生成任务 ID")
        UUID id,
        String projectId,
        UUID specId,
        String requestKey,
        String requestDigest,
        String generationMode,
        List<String> coverageTypes,
        String status,
        String promptKey,
        String promptVersion,
        String modelInvocationId,
        boolean fallbackUsed,
        int apiCount,
        int caseCount,
        Map<String, Object> inputSummary,
        String errorSummary,
        Instant createdAt,
        Instant updatedAt
) {
}
