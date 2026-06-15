package com.songhg.veri.agent.testdata.application.view;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TestAccountLeaseResponse(
        UUID id,
        UUID poolId,
        UUID accountId,
        String projectId,
        String status,
        String holderType,
        String holderRef,
        String requestKey,
        String leaseTokenDigest,
        Instant expiresAt,
        Instant releasedAt,
        String releaseReason,
        TestPooledAccountResponse account,
        Map<String, Object> policy,
        Instant createdAt,
        Instant updatedAt
) {
}
