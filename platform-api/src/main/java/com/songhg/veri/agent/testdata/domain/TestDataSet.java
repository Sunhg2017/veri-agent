package com.songhg.veri.agent.testdata.domain;

import java.time.Instant;
import java.util.UUID;

public record TestDataSet(
        UUID id,
        String projectId,
        String applicationId,
        String environmentId,
        String code,
        String name,
        String status,
        String schemaJson,
        String sensitivityLevel,
        String cleanupPolicyJson,
        String sourceType,
        String sourceRefDigest,
        String createdBy,
        String updatedBy,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
