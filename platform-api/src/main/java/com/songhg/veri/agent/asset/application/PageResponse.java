package com.songhg.veri.agent.asset.application;

import java.time.Instant;
import java.util.UUID;

public record PageResponse(
        UUID id,
        String code,
        String name,
        String urlPattern,
        String source,
        String sourceRef,
        String sourceVersion,
        String componentTree,
        String screenshotUrl,
        String projectId,
        String status,
        String lifecycleStatus,
        Instant archivedAt,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
