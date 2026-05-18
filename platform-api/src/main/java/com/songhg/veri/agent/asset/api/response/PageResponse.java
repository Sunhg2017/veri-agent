package com.songhg.veri.agent.asset.api.response;

import java.time.Instant;
import java.util.UUID;

public record PageResponse(
        UUID id,
        String name,
        String urlPattern,
        String source,
        String sourceRef,
        String componentTree,
        String screenshotUrl,
        String projectId,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
