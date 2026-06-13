package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.UUID;

public record ExecutionTriggerDryRunResponse(
        @Schema(description = "Trigger ID")
        UUID id,
        @Schema(description = "Trigger type")
        String triggerType,
        @Schema(description = "Whether the trigger metadata is valid")
        boolean valid,
        @Schema(description = "Whether webhook/cron global switch is enabled")
        boolean globalEnabled,
        @Schema(description = "Whether a run would be created by dry-run")
        boolean runCreated,
        @Schema(description = "Safe policy summary")
        Map<String, Object> policy
) {
}
