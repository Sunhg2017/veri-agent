package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 上下文装配策略安全边界快照
 */
public record TestDesignContextAssemblyPolicyResponse(
        @Schema(description = "上下文装配策略版本")
        String policyVersion,
        @Schema(description = "上下文装配模式")
        String assemblyMode,
        @Schema(description = "输入摘要计算策略")
        String digestStrategy,
        @Schema(description = "任务是否必须记录输入摘要")
        boolean inputDigestRequired,
        @Schema(description = "是否只持久化脱敏上下文摘要")
        boolean persistedContextSummaryOnly,
        @Schema(description = "是否只通过 WP3 应用服务读取上下文资产")
        boolean wp3ApplicationServiceOnly,
        @Schema(description = "是否持久化原始上下文正文")
        boolean rawContextBodyStored,
        @Schema(description = "是否持久化模型载荷")
        boolean modelPayloadStored,
        @Schema(description = "报告是否导出 digest 原值")
        boolean digestValueExported,
        @Schema(description = "报告是否导出需求正文")
        boolean requirementBodyExported,
        @Schema(description = "报告是否导出 API schema")
        boolean assetSchemaExported,
        @Schema(description = "报告是否导出页面树")
        boolean pageTreeExported,
        @Schema(description = "报告是否导出业务流 JSON")
        boolean flowJsonExported,
        @Schema(description = "报告是否导出显式资产 ID 列表")
        boolean explicitAssetIdentifierListExported,
        @Schema(description = "报告是否导出历史用例步骤")
        boolean historicalCaseStepExported,
        @Schema(description = "是否只暴露聚合装配状态")
        boolean aggregateOnly
) {
}
