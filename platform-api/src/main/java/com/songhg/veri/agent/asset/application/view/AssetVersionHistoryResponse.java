package com.songhg.veri.agent.asset.application.view;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssetVersionHistoryResponse(
        UUID id,
        String assetType,
        UUID assetId,
        String projectId,
        int version,
        String changeType,
        String actor,
        List<String> changedFields,
        JsonNode diff,
        JsonNode snapshot,
        String traceId,
        Instant createdAt
) {
}
