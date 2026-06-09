package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Requests a task-scoped WP5 release-readiness quality-gate exception.
 */
public record RequestTestDesignReleaseReadinessApprovalCommand(
        @Schema(description = "例外原因编码")
        String exceptionReasonCode,
        @Schema(description = "例外摘要，最多 1000 字；不得包含候选正文、密钥、Prompt 或模型载荷")
        String exceptionSummary,
        @Schema(description = "风险缓释说明，最多 1000 字；不得包含敏感正文")
        String riskMitigation,
        @Schema(description = "审批工单编号；为空时由系统生成")
        String workOrderKey,
        @Schema(description = "审批工单标题")
        String workOrderTitle,
        @Schema(description = "审批工单 URL；仅允许 http/https")
        String workOrderUrl,
        @Schema(description = "申请备注，最多 1000 字；不得包含敏感正文")
        String requestNote
) {
}
