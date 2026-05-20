package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record AssetPage(
        UUID id,
        String code,
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
