package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;

/**
 * Aggregate WP2 model observation bucket for WP5 operations drilldown.
 */
public record TestDesignModelObservationBucket(
        String dimension,
        String bucketKey,
        String bucketLabel,
        long invocationCount,
        long succeededCount,
        long failedCount,
        long blockedCount,
        long fallbackCount,
        long inputTokenTotal,
        long outputTokenTotal,
        long latencyMsTotal,
        long averageLatencyMs,
        String totalCostText,
        long traceSignalCount,
        long jobSignalCount,
        Instant latestInvocationAt
) {
}
