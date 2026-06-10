package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;

/**
 * Count-only WP5 operations audit aggregate for batch governance reporting.
 */
public record TestDesignOperationsAuditAggregate(
        long totalOperationCount,
        long successCount,
        long failedCount,
        long deniedCount,
        long queueAlertSubscriptionMutationCount,
        long queuedEventReplayCount,
        long publishCompensationRunCount,
        long auditOutboxRequeueCount,
        Instant latestOperationAt
) {
}
