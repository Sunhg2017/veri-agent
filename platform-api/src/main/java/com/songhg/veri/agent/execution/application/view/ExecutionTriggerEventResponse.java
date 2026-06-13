package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ExecutionTriggerEventResponse(
        @Schema(description = "Trigger event ID")
        UUID id,
        @Schema(description = "Trigger ID")
        UUID triggerId,
        @Schema(description = "External source event ID")
        String sourceEventId,
        @Schema(description = "Digest of the webhook request")
        String requestDigest,
        @Schema(description = "Trigger event status")
        String status,
        @Schema(description = "Run created by this event")
        UUID runId,
        @Schema(description = "Receive time")
        Instant receivedAt,
        @Schema(description = "Sanitized error code")
        String errorCode,
        @Schema(description = "Sanitized error summary")
        String errorSummary,
        @Schema(description = "Trace ID")
        String traceId
) {
}
