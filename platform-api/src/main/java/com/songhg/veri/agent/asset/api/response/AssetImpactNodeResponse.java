package com.songhg.veri.agent.asset.api.response;

import java.time.Instant;
import java.util.UUID;

public record AssetImpactNodeResponse(
        String assetType,
        UUID id,
        String code,
        String title,
        String projectId,
        String status,
        String lifecycleStatus,
        Instant updatedAt
) {
}
