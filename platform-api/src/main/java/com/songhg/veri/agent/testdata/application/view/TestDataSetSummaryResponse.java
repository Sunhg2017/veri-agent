package com.songhg.veri.agent.testdata.application.view;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TestDataSetSummaryResponse(
        UUID id,
        String projectId,
        String applicationId,
        String environmentId,
        String code,
        String name,
        String status,
        String sensitivityLevel,
        String sourceType,
        String sourceRefDigest,
        long recordCount,
        Map<String, Object> cleanupPolicy,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
