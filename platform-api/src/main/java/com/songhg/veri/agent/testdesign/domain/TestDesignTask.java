package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

public record TestDesignTask(
        UUID id,
        String projectId,
        String title,
        String status,
        String requirementIds,
        String coverageTypes,
        String promptKey,
        String promptVersion,
        UUID modelInvocationId,
        String modelProviderName,
        String modelName,
        int totalRequirements,
        int generatedCount,
        int confirmedCount,
        int publishedCount,
        String errorMessage,
        String requestedBy,
        String idempotencyKey,
        String requestDigest,
        String inputDigest,
        String contextSummaryJson,
        Instant createdAt,
        Instant updatedAt
) {
}
