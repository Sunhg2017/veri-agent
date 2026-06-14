package com.songhg.veri.agent.execution.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HeartbeatExecutionQueueClaimCommand(
        @NotBlank
        @Size(max = 128)
        @Schema(description = "Queue claim token")
        String claimToken
) {
}
