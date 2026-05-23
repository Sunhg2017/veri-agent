package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestCaseRecord(
        UUID id,
        String code,
        String title,
        String description,
        String projectId,
        UUID requirementId,
        UUID apiId,
        String source,
        String sourceRef,
        String status,
        String priority,
        String tags,
        List<TestCaseStep> steps,
        int version,
        String lifecycleStatus,
        Instant archivedAt,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) implements LifecycleManagedAsset {
    public boolean canTransitionReviewStatusTo(String nextStatus) {
        return AssetReviewStatus.canTransition(status, nextStatus);
    }

    public TestCaseRecord(
            UUID id,
            String code,
            String title,
            String description,
            String projectId,
            UUID requirementId,
            UUID apiId,
            String source,
            String sourceRef,
            String status,
            String priority,
            String tags,
            int version,
            String lifecycleStatus,
            Instant archivedAt,
            Instant deletedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id,
                code,
                title,
                description,
                projectId,
                requirementId,
                apiId,
                source,
                sourceRef,
                status,
                priority,
                tags,
                List.of(),
                version,
                lifecycleStatus,
                archivedAt,
                deletedAt,
                createdAt,
                updatedAt
        );
    }
}
