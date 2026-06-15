package com.songhg.veri.agent.testdata.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TestPooledAccountResponse(
        UUID id,
        UUID poolId,
        String projectId,
        String accountKey,
        String displayName,
        String status,
        List<String> roleTags,
        Map<String, Object> scopeSummary,
        String secretRefDigest,
        String lastHealthStatus,
        String lastHealthSummary,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
