package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;

/**
 * Redacted cross-WP audit detail bucket; never carries event IDs, trace IDs, sourceRef values or actor identifiers.
 */
public record TestDesignCrossWpAuditDetailBucket(
        String section,
        String category,
        String status,
        long eventCount,
        long successCount,
        long failedCount,
        long warningCount,
        Instant latestEventAt
) {
}
