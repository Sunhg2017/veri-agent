package com.songhg.veri.agent.apiautomation.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * WP6 从脱敏 OpenAPI 规格中抽取的 endpoint 摘要。
 */
public record ApiAutomationEndpointSnapshot(
        UUID id,
        UUID specId,
        String projectId,
        String serviceName,
        String operationId,
        String httpMethod,
        String path,
        String summary,
        String tags,
        int parameterCount,
        boolean requestBodyPresent,
        String responseStatuses,
        String schemaDigest,
        String diffStatus,
        UUID assetApiId,
        String diffSummaryJson,
        Instant lastDiffAt,
        Instant syncedAt,
        String syncErrorSummary,
        Instant createdAt,
        Instant updatedAt
) {
}
