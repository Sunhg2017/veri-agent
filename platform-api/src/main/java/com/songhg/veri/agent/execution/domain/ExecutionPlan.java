package com.songhg.veri.agent.execution.domain;

import java.time.Instant;
import java.util.UUID;

public record ExecutionPlan(
        UUID id,
        String projectId,
        String name,
        String status,
        String environmentKey,
        String triggerPolicyJson,
        String dagDigest,
        String description,
        String createdBy,
        String updatedBy,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
