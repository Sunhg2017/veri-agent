package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Creates a pending WP5 context policy override request for project or environment scope.
 */
public record RequestTestDesignContextPolicyOverrideCommand(
        @Schema(description = "每需求关联资产数量上限；为空表示沿用上级策略")
        Integer contextLinkedAssetsPerRequirement,
        @Schema(description = "每类显式上下文资产数量上限；为空表示沿用上级策略")
        Integer contextExplicitAssetsPerType,
        @Schema(description = "每需求历史用例数量上限；为空表示沿用上级策略")
        Integer contextExistingCasesPerRequirement,
        @Schema(description = "需求描述摘要字符上限；为空表示沿用上级策略")
        Integer contextRequirementDescriptionChars,
        @Schema(description = "验收标准摘要字符上限；为空表示沿用上级策略")
        Integer contextAcceptanceCriteriaChars,
        @Schema(description = "API schema、页面树、流程 JSON 摘要字符上限；为空表示沿用上级策略")
        Integer contextAssetSchemaChars,
        @Schema(description = "变更原因编码")
        String changeReasonCode,
        @Schema(description = "策略正文，最多 4000 字；不得包含密钥、原始上下文或 Prompt 载荷")
        String policyBody,
        @Schema(description = "策略 diff 摘要，最多 1000 字；不得包含敏感正文")
        String policyDiffSummary,
        @Schema(description = "审批工单编号；为空时由系统生成")
        String workOrderKey,
        @Schema(description = "审批工单标题")
        String workOrderTitle,
        @Schema(description = "审批工单 URL；仅允许 http/https")
        String workOrderUrl,
        @Schema(description = "申请备注，最多 1000 字；不得包含密钥、原始上下文或 Prompt 载荷")
        String requestNote
) {
}
