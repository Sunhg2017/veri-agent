package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record TraceLink(
        UUID id,
        UUID requirementId,
        UUID apiId,
        UUID caseId,
        Instant createdAt
) {
}
