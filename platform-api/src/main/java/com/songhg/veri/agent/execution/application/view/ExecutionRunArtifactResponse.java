package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ExecutionRunArtifactResponse(
        @Schema(description = "Execution artifact ID")
        UUID id,
        @Schema(description = "Owning node run ID")
        UUID nodeRunId,
        @Schema(description = "Source plan node ID")
        UUID planNodeId,
        @Schema(description = "Source plan node key")
        String nodeKey,
        @Schema(description = "Source plan node type")
        String nodeType,
        @Schema(description = "Runner integration type")
        String runnerType,
        @Schema(description = "Artifact provider, for example WP7_UI_E2E")
        String sourceType,
        @Schema(description = "Artifact type, for example LOG or SCREENSHOT")
        String artifactType,
        @Schema(description = "Sanitized artifact digest")
        String artifactDigest,
        @Schema(description = "Artifact size in bytes")
        long sizeBytes,
        @Schema(description = "Capture status")
        String captureStatus,
        @Schema(description = "Whether raw download is currently allowed")
        boolean downloadReady,
        @Schema(description = "Sanitized redaction flags")
        Map<String, Object> redactionFlags,
        Instant createdAt,
        Instant updatedAt
) {
}
