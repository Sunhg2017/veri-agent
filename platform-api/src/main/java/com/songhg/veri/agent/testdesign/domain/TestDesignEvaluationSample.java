package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Curated WP5 evaluation sample used by prompt calibration and golden-set regression.
 */
public record TestDesignEvaluationSample(
        UUID id,
        String projectId,
        String sampleKey,
        String title,
        String sourceType,
        UUID sourceTaskId,
        UUID sourceCandidateId,
        String promptKey,
        String promptVersion,
        String coverageType,
        String priority,
        String status,
        String baselineVersion,
        String requirementSummary,
        String expectedCaseOutline,
        String assertionNotes,
        String tags,
        String maintenanceNote,
        String sampleDigest,
        String sensitiveScanStatus,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
