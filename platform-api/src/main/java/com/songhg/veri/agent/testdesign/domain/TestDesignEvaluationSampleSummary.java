package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;

/**
 * Aggregate sample-maintenance counters for WP5 evaluation corpus operations.
 */
public record TestDesignEvaluationSampleSummary(
        long totalCount,
        long candidateCount,
        long goldenCount,
        long frozenCount,
        long deprecatedCount,
        long baselineVersionCount,
        Instant latestUpdatedAt
) {
}
