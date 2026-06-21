package com.songhg.veri.agent.uie2e.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BatchCreateUiE2eRunCommand(
        @NotBlank @Size(max = 64) String projectId,
        @NotEmpty @Size(max = 100) List<@NotNull UUID> sceneIds,
        @Size(max = 128) String environmentId,
        @NotBlank @Size(max = 128) String baseUrlRef,
        @NotNull UUID accountLeaseRef,
        @Size(max = 64) String requestKeyPrefix,
        @Size(max = 512) String reason,
        List<String> browsers,
        Boolean visualRegressionEnabled,
        UUID baselineRunId,
        Double visualMismatchThreshold
) {
}
