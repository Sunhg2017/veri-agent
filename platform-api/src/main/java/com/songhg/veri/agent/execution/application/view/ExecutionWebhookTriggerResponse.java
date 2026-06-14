package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record ExecutionWebhookTriggerResponse(
        @Schema(description = "Trigger event")
        ExecutionTriggerEventResponse event,
        @Schema(description = "Run ID created or replayed by this webhook")
        UUID runId,
        @Schema(description = "Whether this webhook replayed an existing event")
        boolean idempotentReplay
) {
}
