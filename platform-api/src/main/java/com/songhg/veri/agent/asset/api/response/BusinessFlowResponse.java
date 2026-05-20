package com.songhg.veri.agent.asset.api.response;

import java.time.Instant;
import java.util.UUID;

public record BusinessFlowResponse(
        UUID id,
        String code,
        String name,
        String description,
        String flowJson,
        String priority,
        String projectId,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
