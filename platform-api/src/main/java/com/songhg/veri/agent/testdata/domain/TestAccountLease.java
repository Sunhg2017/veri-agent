package com.songhg.veri.agent.testdata.domain;

import java.time.Instant;
import java.util.UUID;

public record TestAccountLease(
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
        String releaseReason,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
