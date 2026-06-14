package com.songhg.veri.agent.testdata.domain;

import java.time.Instant;
import java.util.UUID;

public record TestPooledAccount(
        UUID id,
        UUID poolId,
        String projectId,
        String accountKey,
        String displayName,
        String status,
        String roleTagsJson,
        String scopeSummaryJson,
        String secretRefDigest,
        String lastHealthStatus,
        String lastHealthSummary,
        String createdBy,
        String updatedBy,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
