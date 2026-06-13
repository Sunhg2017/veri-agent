package com.songhg.veri.agent.execution.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

public record UpdateExecutionTriggerCommand(
        @Schema(description = "Trigger status")
        @Pattern(regexp = "DISABLED|ENABLED|PAUSED|disabled|enabled|paused", message = "status is invalid")
        String status,
        @Schema(description = "Safe trigger config summary; secrets are rejected")
        Map<String, Object> config,
        @Schema(description = "Webhook signing secret reference")
        @Size(max = 256) String secretRef,
        @Schema(description = "Cron next fire time metadata")
        Instant nextFireAt
) {
}
