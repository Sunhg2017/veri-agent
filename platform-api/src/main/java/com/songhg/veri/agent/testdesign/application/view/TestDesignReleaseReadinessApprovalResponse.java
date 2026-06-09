package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Sanitized WP5 release-readiness approval view for operations APIs.
 */
public record TestDesignReleaseReadinessApprovalResponse(
        @Schema(description = "审批记录 ID")
        UUID id,
        @Schema(description = "任务 ID")
        UUID taskId,
        @Schema(description = "项目 ID")
        String projectId,
        @Schema(description = "审批状态")
        String status,
        @Schema(description = "审批捕获的质量门禁聚合状态")
        String qualityGateStatus,
        @Schema(description = "审批捕获的阻断项数量")
        long blockingCount,
        @Schema(description = "审批捕获的风险提示数量")
        long warningCount,
        @Schema(description = "聚合准出摘要 SHA-256，用于防止旧例外覆盖新问题")
        String readinessDigest,
        @Schema(description = "是否捕获例外原因编码")
        boolean exceptionReasonCodeCaptured,
        @Schema(description = "例外原因编码")
        String exceptionReasonCode,
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
        @Schema(description = "例外摘要；仅在运营接口返回，不进入任务诊断或报告")
        String exceptionSummary,
        @Schema(description = "例外摘要 SHA-256")
        String exceptionSummaryDigest,
        @Schema(description = "风险缓释说明；仅在运营接口返回")
        String riskMitigation,
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
