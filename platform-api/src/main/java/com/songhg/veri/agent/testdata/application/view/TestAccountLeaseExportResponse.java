package com.songhg.veri.agent.testdata.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TestAccountLeaseExportResponse(
        String schemaVersion,
        Instant exportedAt,
        LeaseSnapshot lease,
        PoolSnapshot pool,
        AccountSnapshot account,
        Map<String, Object> lifecycleSummary,
        Map<String, Object> redactionPolicy
) {

    public record LeaseSnapshot(
            UUID id,
            UUID poolId,
            UUID accountId,
            String projectId,
            String status,
            String holderType,
            String holderRef,
            String requestKey,
            String requestDigest,
            String leaseTokenDigest,
            Instant expiresAt,
            Instant releasedAt,
            boolean releaseReasonPresent,
            String releaseReasonDigest,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PoolSnapshot(
            UUID id,
            String projectId,
            String applicationId,
            String environmentId,
            String code,
            String name,
            String status,
            int defaultTtlSeconds,
            List<String> leasePolicyKeys,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record AccountSnapshot(
            UUID id,
            UUID poolId,
            String projectId,
            String accountKey,
            String displayName,
            String status,
            List<String> roleTags,
            List<String> scopeSummaryKeys,
            String secretRefDigest,
            String lastHealthStatus,
            boolean lastHealthSummaryPresent,
            String lastHealthSummaryDigest,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
