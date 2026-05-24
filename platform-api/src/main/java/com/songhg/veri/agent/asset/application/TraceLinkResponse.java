package com.songhg.veri.agent.asset.application;

import java.time.Instant;
import java.util.UUID;

public record TraceLinkResponse(
        UUID id,
        UUID requirementId,
        UUID apiId,
        UUID pageId,
        UUID flowId,
        UUID caseId,
        Instant createdAt
) {
}
