package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Approval work order for report archive finalization or external sharing.
 */
public record TestDesignReportArchiveApproval(
        UUID id,
        UUID archiveId,
        UUID taskId,
        String projectId,
        String approvalType,
        String status,
        String reasonCode,
        String approvalReasonCode,
        String workOrderKey,
        String workOrderTitle,
        String workOrderUrl,
        String workOrderStatus,
        String requestSummary,
        String requestSummaryDigest,
        String requestNote,
        String reviewNote,
        String requestedBy,
        String approvedBy,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
