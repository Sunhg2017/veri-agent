package com.songhg.veri.agent.execution.domain;

import java.time.Instant;
import java.util.UUID;

public record ExecutionTrigger(
        UUID id,
        UUID planId,
        String triggerType,
        String status,
        String configDigest,
        String configSummaryJson,
        String secretRef,
        String secretRefDigest,
        Instant nextFireAt,
        Instant lastFireAt,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
