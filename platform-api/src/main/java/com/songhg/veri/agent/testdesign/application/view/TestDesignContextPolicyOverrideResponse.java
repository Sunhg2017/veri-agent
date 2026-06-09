package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sanitized WP5 context policy override view for operations APIs.
 */
public record TestDesignContextPolicyOverrideResponse(
        @Schema(description = "策略覆盖记录 ID")
        UUID id,
        @Schema(description = "覆盖作用域类型：PROJECT 或 ENVIRONMENT")
        String scopeType,
        @Schema(description = "项目 ID")
        String projectId,
        @Schema(description = "环境键；项目级覆盖为空")
        String environmentKey,
        @Schema(description = "审批状态")
        String status,
        @Schema(description = "覆盖的上下文裁剪上限；只返回数字，不返回策略正文")
        Map<String, Integer> overrideLimits,
        @Schema(description = "是否捕获变更原因编码")
        boolean changeReasonCodeCaptured,
        @Schema(description = "是否捕获审批原因编码")
        boolean approvalReasonCodeCaptured,
        @Schema(description = "审批工单编号")
        String workOrderKey,
        @Schema(description = "审批工单标题")
        String workOrderTitle,
        @Schema(description = "审批工单 URL")
        String workOrderUrl,
        @Schema(description = "审批工单状态")
        String workOrderStatus,
        @Schema(description = "策略正文；仅在策略运营接口返回，不进入任务诊断或报告")
        String policyBody,
        @Schema(description = "策略正文 SHA-256 摘要")
        String policyBodyDigest,
        @Schema(description = "策略正文版本号")
        Integer policyBodyVersion,
        @Schema(description = "策略 diff 摘要")
        String policyDiffSummary,
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
