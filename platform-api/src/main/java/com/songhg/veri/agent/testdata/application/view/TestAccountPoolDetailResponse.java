package com.songhg.veri.agent.testdata.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TestAccountPoolDetailResponse(
        UUID id,
        String projectId,
        String applicationId,
        String environmentId,
        String code,
        String name,
        String status,
        Map<String, Object> leasePolicy,
        int defaultTtlSeconds,
        List<TestPooledAccountResponse> accounts,
        Map<String, Object> policy,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
