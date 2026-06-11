package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ApiAutomationSpecResponse(
        @Schema(description = "规格 ID")
        UUID id,
        String projectId,
        String sourceType,
        String sourceRef,
        String name,
        String versionLabel,
        String specDigest,
        int contentSizeBytes,
        String status,
        String parserVersion,
        int endpointCount,
        String parseErrorSummary,
        Instant parsedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
