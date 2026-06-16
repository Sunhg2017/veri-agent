package com.songhg.veri.agent.reporting.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "WP10 sanitized report export response")
public record ReportExportResponse(
        UUID id,
        UUID reportId,
        String exportType,
        String status,
        String schemaVersion,
        String fieldSetVersion,
        String contentDigest,
        boolean aggregateOnly,
        String exportedBy,
        Instant exportedAt,
        String blockReason,
        Map<String, Object> redactionPolicy,
        Map<String, Object> manifest,
        Object content,
        Instant createdAt
) {
}
