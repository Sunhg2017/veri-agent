package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestCaseRecord(
        UUID id,
        String title,
        String description,
        String projectId,
        UUID requirementId,
        UUID apiId,
        String status,
        String priority,
        String tags,
        List<TestCaseStep> steps,
        Instant createdAt,
        Instant updatedAt
) {
}
