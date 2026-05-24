package com.songhg.veri.agent.asset.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestCaseResponse(
        UUID id,
        String code,
        String title,
        String description,
        UUID requirementId,
        UUID apiId,
        String source,
        String sourceRef,
        String projectId,
        String status,
        String priority,
        String tags,
        List<TestCaseStepResponse> steps,
        int version,
        String lifecycleStatus,
        Instant archivedAt,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
