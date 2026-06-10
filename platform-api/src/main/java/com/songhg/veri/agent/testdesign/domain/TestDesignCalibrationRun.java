package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Versioned WP5 prompt calibration run bound to a maintained sample baseline.
 */
public record TestDesignCalibrationRun(
        UUID id,
        String projectId,
        String promptKey,
        String promptVersion,
        String baselineVersion,
        String runMode,
        String status,
        long sampleCount,
        long goldenSampleCount,
        long taskCount,
        long candidateCount,
        double stepCompletePercent,
        double expectedCompletePercent,
        double lowConfidencePercent,
        double errorPercent,
        long duplicateKeyCollisionCount,
        long feedbackSignalCount,
        String readinessStatus,
        long readinessBlockingCount,
        long readinessWarningCount,
        long regressionCount,
        String baselineDigest,
        String resultDigest,
        String notes,
        String runBy,
        Instant createdAt
) {
}
