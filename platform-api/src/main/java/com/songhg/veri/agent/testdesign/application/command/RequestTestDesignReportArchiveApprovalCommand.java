package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Requests a report archive or external sharing approval work order.
 */
public record RequestTestDesignReportArchiveApprovalCommand(
        @Schema(description = "申请原因编码")
        String reasonCode,
        @Schema(description = "申请摘要，最多 1000 字；不得包含报告正文、密钥、Prompt 或模型载荷")
        String requestSummary,
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
