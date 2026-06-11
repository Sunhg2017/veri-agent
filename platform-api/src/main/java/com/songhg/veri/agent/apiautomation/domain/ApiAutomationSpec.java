package com.songhg.veri.agent.apiautomation.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * WP6 OpenAPI 源的脱敏规格快照。
 */
public record ApiAutomationSpec(
        UUID id,
        String projectId,
        String sourceType,
        String sourceRef,
        String name,
        String versionLabel,
        String specDigest,
        int contentSizeBytes,
        String sanitizedSpecJson,
        String parseSummaryJson,
        String status,
        String parserVersion,
        int endpointCount,
        String parseErrorSummary,
        String createdBy,
        String updatedBy,
        Instant parsedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
