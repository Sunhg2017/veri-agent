package com.songhg.veri.agent.execution.domain;

import java.time.Instant;
import java.util.UUID;

public record ExecutionQueueClaim(
        UUID id,
        UUID nodeRunId,
        String claimToken,
        String workerId,
        Instant claimedAt,
        Instant heartbeatAt,
        Instant expiresAt,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
