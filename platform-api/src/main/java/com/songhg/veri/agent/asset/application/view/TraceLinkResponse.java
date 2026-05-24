package com.songhg.veri.agent.asset.application.view;

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
