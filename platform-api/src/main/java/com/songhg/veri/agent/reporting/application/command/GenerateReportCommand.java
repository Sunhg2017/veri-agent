package com.songhg.veri.agent.reporting.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GenerateReportCommand(
        @NotBlank
        @Size(max = 64)
        @Schema(description = "Owning project scope ID")
        String projectId,
        @NotNull
        @Schema(description = "Source WP9 execution run ID")
        UUID executionRunId,
        @Size(max = 128)
        @Schema(description = "Client idempotency key for report generation")
        String requestKey,
        @Size(max = 256)
        @Schema(description = "Bounded generation reason")
        String reason
) {
}
