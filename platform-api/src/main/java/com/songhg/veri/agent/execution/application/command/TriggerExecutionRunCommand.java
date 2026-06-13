package com.songhg.veri.agent.execution.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record TriggerExecutionRunCommand(
        @Schema(description = "Manual trigger idempotency key")
        @Size(max = 128) String requestKey,
        @Schema(description = "Bounded manual trigger reason")
        @Size(max = 256) String reason,
        @Schema(description = "Optional trigger variables; values are not persisted by WP9 M3A")
        Map<String, Object> variables
) {
}
