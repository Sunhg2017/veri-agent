package com.songhg.veri.agent.uie2e.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpsertUiE2eFlakyMarkCommand(
        @NotBlank @Size(max = 64) String projectId,
        UUID sceneId,
        UUID runId,
        @NotBlank @Size(max = 32) String status,
        @Size(max = 64) String reasonCode,
        @Size(max = 512) String reasonSummary
) {
}
