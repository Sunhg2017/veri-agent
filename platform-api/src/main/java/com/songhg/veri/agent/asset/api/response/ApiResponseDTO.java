package com.songhg.veri.agent.asset.api.response;

import java.time.Instant;
import java.util.UUID;

public record ApiResponseDTO(
        UUID id,
        String summary,
        String description,
        String httpMethod,
        String path,
        String requestSchema,
        String responseSchema,
        String projectId,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
