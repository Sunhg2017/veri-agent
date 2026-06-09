package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Reviews a pending WP5 context policy override and appends the review note to the approval work order timeline.
 */
public record ReviewTestDesignContextPolicyOverrideCommand(
        @Schema(description = "审批原因编码")
        String approvalReasonCode,
        @Schema(description = "审批备注，最多 1000 字；不得包含密钥、原始上下文或 Prompt 载荷")
        String reviewNote,
        @Schema(description = "审批后的工单状态；为空时跟随审批动作")
        String workOrderStatus
) {
}
