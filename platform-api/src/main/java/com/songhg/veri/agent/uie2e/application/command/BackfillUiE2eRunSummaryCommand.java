package com.songhg.veri.agent.uie2e.application.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BackfillUiE2eRunSummaryCommand(
        @NotBlank @Size(max = 64) String projectId,
        @Size(max = 200) List<UUID> runIds,
        @Max(200) Integer limit
) {
}
