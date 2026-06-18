package com.songhg.veri.agent.uie2e.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateUiE2eRunCommand(
        @NotBlank @Size(max = 64) String projectId,
        @NotNull UUID sceneId,
        @NotNull UUID bundleId,
        @Size(max = 128) String environmentId,
        @NotBlank @Size(max = 128) String baseUrlRef,
        @NotNull UUID accountLeaseRef,
        @Size(max = 128) String requestKey,
        @Size(max = 512) String reason
) {
}
