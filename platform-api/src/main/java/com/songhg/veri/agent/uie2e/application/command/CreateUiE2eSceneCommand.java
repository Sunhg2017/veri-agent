package com.songhg.veri.agent.uie2e.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CreateUiE2eSceneCommand(
        @NotBlank @Size(max = 64) String projectId,
        @Size(max = 64) String applicationId,
        @Size(max = 64) String environmentId,
        @NotBlank @Size(max = 128) String code,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 32) String status,
        @Size(max = 32) String riskLevel,
        List<@Size(max = 32) String> tags,
        Map<String, Object> sourceSummary,
        @NotEmpty List<@Valid SceneStepPayload> steps
) {

    public record SceneStepPayload(
            @NotBlank @Size(max = 32) String stepType,
            Map<String, Object> actionSummary,
            Map<String, Object> locatorStrategy,
            Map<String, Object> assertionSummary,
            Map<String, Object> waitPolicy,
            Map<String, Object> dataBinding
    ) {

        public SceneStepPayload(
                String stepType,
                Map<String, Object> actionSummary,
                Map<String, Object> locatorStrategy,
                Map<String, Object> assertionSummary,
                Map<String, Object> waitPolicy
        ) {
            this(stepType, actionSummary, locatorStrategy, assertionSummary, waitPolicy, null);
        }
    }
}
