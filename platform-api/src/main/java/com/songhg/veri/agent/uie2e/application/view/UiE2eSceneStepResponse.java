package com.songhg.veri.agent.uie2e.application.view;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UiE2eSceneStepResponse(
        UUID id,
        int stepOrder,
        String stepType,
        Map<String, Object> actionSummary,
        Map<String, Object> locatorStrategy,
        Map<String, Object> assertionSummary,
        Map<String, Object> waitPolicy,
        Instant createdAt,
        Instant updatedAt
) {
}
