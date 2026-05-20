package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record AssetVersionHistory(
        UUID id,
        String assetType,
        UUID assetId,
        String projectId,
        int version,
        String changeType,
        String actor,
        String changedFields,
        String diffJson,
        String snapshotJson,
        String traceId,
        Instant createdAt
) {
}
