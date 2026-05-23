package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record AssetRequirement(
        UUID id,
        String code,
        String title,
        String description,
        String source,
        String sourceRef,
        String sourceUrl,
        String acceptanceCriteria,
        String status,
        String priority,
        String projectId,
        String tags,
        int version,
        String lifecycleStatus,
        Instant archivedAt,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean canTransitionReviewStatusTo(String nextStatus) {
        return AssetReviewStatus.canTransition(status, nextStatus);
    }
}
