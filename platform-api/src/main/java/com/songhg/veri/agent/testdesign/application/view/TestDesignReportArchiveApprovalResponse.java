package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Sanitized report archive approval view.
 */
public record TestDesignReportArchiveApprovalResponse(
        @Schema(description = "审批记录 ID")
        UUID id,
        @Schema(description = "归档记录 ID")
        UUID archiveId,
        @Schema(description = "任务 ID")
        UUID taskId,
        @Schema(description = "项目 ID")
        String projectId,
        @Schema(description = "审批类型：ARCHIVE 或 EXTERNAL_SHARE")
        String approvalType,
        @Schema(description = "审批状态")
        String status,
        @Schema(description = "是否捕获申请原因编码")
        boolean reasonCodeCaptured,
        @Schema(description = "申请原因编码")
        String reasonCode,
        @Schema(description = "是否捕获审批原因编码")
        boolean approvalReasonCodeCaptured,
        @Schema(description = "审批原因编码")
        String approvalReasonCode,
        @Schema(description = "审批工单编号")
        String workOrderKey,
        @Schema(description = "审批工单标题")
        String workOrderTitle,
        @Schema(description = "审批工单 URL")
        String workOrderUrl,
        @Schema(description = "审批工单状态")
        String workOrderStatus,
        @Schema(description = "申请摘要")
        String requestSummary,
        @Schema(description = "申请摘要 SHA-256")
        String requestSummaryDigest,
        @Schema(description = "申请备注")
        String requestNote,
        @Schema(description = "审批备注")
        String reviewNote,
        @Schema(description = "备注数量")
        long noteCount,
        @Schema(description = "最近一条备注预览")
        String latestNotePreview,
        @Schema(description = "申请人")
        String requestedBy,
        @Schema(description = "审批人")
        String approvedBy,
        @Schema(description = "审批时间")
        Instant reviewedAt,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "更新时间")
        Instant updatedAt
) {
}
