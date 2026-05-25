package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

public record TestDesignCandidate(
        UUID id,
        UUID taskId,
        String projectId,
        UUID requirementId,
        UUID apiId,
        String title,
        String description,
        String coverageType,
        String priority,
        String status,
        String preconditions,
        String stepsJson,
        String expectedResult,
        String tags,
        String duplicateKey,
        double confidence,
        String promptKey,
        String promptVersion,
        UUID modelInvocationId,
        String modelProviderName,
        String modelName,
        UUID assetCaseId,
        String reviewComment,
        String rejectedReason,
        String ignoredReason,
        String errorMessage,
        String confirmedBy,
        Instant confirmedAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
