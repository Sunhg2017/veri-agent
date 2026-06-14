package com.songhg.veri.agent.testdata.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TestDataSetDetailResponse(
        UUID id,
        String projectId,
        String applicationId,
        String environmentId,
        String code,
        String name,
        String status,
        Map<String, Object> schema,
        String sensitivityLevel,
        Map<String, Object> cleanupPolicy,
        String sourceType,
        String sourceRefDigest,
        List<TestDataRecordResponse> records,
        Map<String, Object> policy,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
