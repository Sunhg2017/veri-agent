package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ExecutionTriggerResponse(
        @Schema(description = "Trigger ID")
        UUID id,
        @Schema(description = "Owning plan ID")
        UUID planId,
        @Schema(description = "Trigger type")
        String triggerType,
        @Schema(description = "Trigger status")
        String status,
        @Schema(description = "Digest of normalized trigger config")
        String configDigest,
        @Schema(description = "Safe trigger configuration summary")
        Map<String, Object> configSummary,
        @Schema(description = "Whether a secretRef is configured")
        boolean secretRefConfigured,
        @Schema(description = "Digest of the secretRef")
        String secretRefDigest,
        @Schema(description = "Cron next fire time metadata")
        Instant nextFireAt,
        @Schema(description = "Last accepted fire time")
        Instant lastFireAt,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
