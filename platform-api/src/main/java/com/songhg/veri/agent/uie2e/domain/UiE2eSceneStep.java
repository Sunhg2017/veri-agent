package com.songhg.veri.agent.uie2e.domain;

import java.time.Instant;
import java.util.UUID;

public record UiE2eSceneStep(
        UUID id,
        UUID sceneId,
        String projectId,
        int stepOrder,
        String stepType,
        String actionSummaryJson,
        String locatorStrategyJson,
        String assertionSummaryJson,
        String waitPolicyJson,
        String dataBindingJson,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {

    public UiE2eSceneStep(
            UUID id,
            UUID sceneId,
            String projectId,
            int stepOrder,
            String stepType,
            String actionSummaryJson,
            String locatorStrategyJson,
            String assertionSummaryJson,
            String waitPolicyJson,
            String createdBy,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id,
                sceneId,
                projectId,
                stepOrder,
                stepType,
                actionSummaryJson,
                locatorStrategyJson,
                assertionSummaryJson,
                waitPolicyJson,
                null,
                createdBy,
                updatedBy,
                createdAt,
                updatedAt
        );
    }
}
