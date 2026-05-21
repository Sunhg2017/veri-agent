package com.songhg.veri.agent.asset.api.response;

import java.time.Instant;
import java.util.UUID;

public record ApiResponseDTO(
        UUID id,
        String code,
        String summary,
        String description,
        String httpMethod,
        String path,
        String source,
        String sourceRef,
        String requestSchema,
        String responseSchema,
        String projectId,
        String status,
        String lifecycleStatus,
        Instant archivedAt,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
