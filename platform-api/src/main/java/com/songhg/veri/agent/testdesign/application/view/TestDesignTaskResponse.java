package com.songhg.veri.agent.testdesign.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TestDesignTaskResponse(
        UUID id,
        String projectId,
        String title,
        String status,
        List<UUID> requirementIds,
        List<String> coverageTypes,
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
        String inputDigest,
        Map<String, Object> contextSummary,
        Instant createdAt,
        Instant updatedAt
) {
}
