package com.songhg.veri.agent.management.application;

public record AuditOutboxView(
        String id,
        String traceId,
        String idempotencyKey,
        String status,
        int retryCount,
        String nextRetryAt,
        String lockedAt,
        String lockedBy,
        String lastError,
        String eventAction,
        String resourceType,
        String resourceId,
        String result,
        String createdAt,
        String updatedAt
) {
}
