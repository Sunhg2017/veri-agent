package com.songhg.veri.agent.apiautomation.domain;

import java.time.Instant;
import java.util.UUID;

public record ApiAutomationRunResult(
        UUID id,
        UUID runId,
        UUID caseId,
        String status,
        int durationMs,
        String assertionSummaryJson,
        String errorCode,
        String errorSummary,
        Instant createdAt,
        Instant updatedAt
) {
}
