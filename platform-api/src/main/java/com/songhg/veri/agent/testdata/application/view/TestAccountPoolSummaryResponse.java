package com.songhg.veri.agent.testdata.application.view;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TestAccountPoolSummaryResponse(
        UUID id,
        String projectId,
        String applicationId,
        String environmentId,
        String code,
        String name,
        String status,
        Map<String, Object> leasePolicy,
        int defaultTtlSeconds,
        long accountCount,
        long availableAccountCount,
        long lockedAccountCount,
        long disabledAccountCount,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
