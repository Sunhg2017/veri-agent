package com.songhg.veri.agent.apiautomation.domain;

import java.time.Instant;
import java.util.UUID;

public record ApiAutomationGenerationTask(
        UUID id,
        String projectId,
        UUID specId,
        String requestKey,
        String requestDigest,
        String generationMode,
        String coverageTypesJson,
        String status,
        String promptKey,
        String promptVersion,
        String modelInvocationId,
        boolean fallbackUsed,
        int apiCount,
        int caseCount,
        String inputSummaryJson,
        String errorSummary,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
