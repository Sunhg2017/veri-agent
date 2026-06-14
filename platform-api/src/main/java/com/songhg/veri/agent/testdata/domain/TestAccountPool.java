package com.songhg.veri.agent.testdata.domain;

import java.time.Instant;
import java.util.UUID;

public record TestAccountPool(
        UUID id,
        String projectId,
        String applicationId,
        String environmentId,
        String code,
        String name,
        String status,
        String leasePolicyJson,
        int defaultTtlSeconds,
        String createdBy,
        String updatedBy,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
