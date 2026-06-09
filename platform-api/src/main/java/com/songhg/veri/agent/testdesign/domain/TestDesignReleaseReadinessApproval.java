package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Task-scoped WP5 release-readiness approval and quality-gate exception.
 *
 * <p>The aggregate stores only bounded approval metadata and aggregate readiness counters. Candidate evidence, threshold
 * rule details and approval notes stay out of task diagnostics and reports; notes are exposed only through the
 * permission-guarded operations API.</p>
 */
public record TestDesignReleaseReadinessApproval(
        UUID id,
        UUID taskId,
        String projectId,
        String status,
        String qualityGateStatus,
        long blockingCount,
        long warningCount,
        String readinessDigest,
        String exceptionReasonCode,
        String approvalReasonCode,
        String workOrderKey,
        String workOrderTitle,
        String workOrderUrl,
        String workOrderStatus,
        String exceptionSummary,
        String exceptionSummaryDigest,
        String riskMitigation,
        String requestNote,
        String reviewNote,
        String requestedBy,
        String approvedBy,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
