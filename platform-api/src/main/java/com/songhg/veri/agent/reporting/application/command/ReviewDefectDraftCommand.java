package com.songhg.veri.agent.reporting.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewDefectDraftCommand(
        @NotBlank
        @Size(max = 32)
        @Schema(description = "Next defect draft status: REVIEWED, DISMISSED or DRAFT restore")
        String status
) {
}
