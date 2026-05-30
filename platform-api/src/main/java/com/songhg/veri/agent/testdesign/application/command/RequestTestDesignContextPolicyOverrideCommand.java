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
        @Schema(description = "变更原因编码；仅保存枚举化编码，不保存自由文本")
        String changeReasonCode
) {
}
