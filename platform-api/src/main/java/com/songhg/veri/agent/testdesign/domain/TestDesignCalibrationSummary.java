package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;

/**
 * Aggregate long-term calibration counters for WP5 prompt operations.
 */
public record TestDesignCalibrationSummary(
        long totalRunCount,
        long passedRunCount,
        long warningRunCount,
        long blockedRunCount,
        String latestStatus,
        Instant latestRunAt
) {
}
