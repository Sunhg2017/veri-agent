package com.songhg.veri.agent.uie2e.domain;

import java.time.Instant;
import java.util.UUID;

public record UiE2eRun(
        UUID id,
        UUID sceneId,
        UUID bundleId,
        String projectId,
        String status,
        String requestKey,
        String runnerMode,
        String baseUrlDigest,
        String accountLeaseRef,
        String accountSummaryJson,
        String failureCode,
        String failureSummary,
        String traceId,
        String createdBy,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
