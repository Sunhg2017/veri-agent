package com.songhg.veri.agent.uie2e.application.view;

import java.util.Map;

public record UiE2eSceneImportStepResponse(
        int stepOrder,
        String stepType,
        Map<String, Object> actionSummary,
        Map<String, Object> locatorStrategy,
        Map<String, Object> assertionSummary,
        Map<String, Object> waitPolicy,
        Map<String, Object> dataBinding
) {

    public UiE2eSceneImportStepResponse(
            int stepOrder,
            String stepType,
            Map<String, Object> actionSummary,
            Map<String, Object> locatorStrategy,
            Map<String, Object> assertionSummary,
            Map<String, Object> waitPolicy
    ) {
        this(stepOrder, stepType, actionSummary, locatorStrategy, assertionSummary, waitPolicy, Map.of());
    }
}
