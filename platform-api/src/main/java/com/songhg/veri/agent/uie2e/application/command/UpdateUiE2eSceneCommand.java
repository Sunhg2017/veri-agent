package com.songhg.veri.agent.uie2e.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record UpdateUiE2eSceneCommand(
        @Size(max = 64) String applicationId,
        @Size(max = 64) String environmentId,
        @Size(max = 128) String name,
        @Size(max = 32) String status,
        @Size(max = 32) String riskLevel,
        List<@Size(max = 32) String> tags,
        Map<String, Object> sourceSummary,
        @Valid List<CreateUiE2eSceneCommand.SceneStepPayload> steps
) {
}
