package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Aggregate-only publish compensation runbook state.
 */
public record TestDesignCompensationRunbookResponse(
        @Schema(description = "策略版本")
        String policyVersion,
        @Schema(description = "所属项目 ID")
        String projectId,
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "补偿是否启用")
        boolean compensationEnabled,
        @Schema(description = "自动调度是否就绪")
        boolean automaticScheduleReady,
        @Schema(description = "是否支持人工运行")
        boolean manualRunSupported,
        @Schema(description = "是否支持项目/prompt 作用域运行")
        boolean scopedRunSupported,
        @Schema(description = "默认补偿批量大小")
        int effectiveBatchSize,
        @Schema(description = "可补偿候选数量")
        long eligibleCandidateCount,
        @Schema(description = "是否允许自动首次创建 WP3 用例")
        boolean autoFirstCreateAllowed,
        @Schema(description = "是否允许自动解决冲突")
        boolean autoConflictResolveAllowed,
        @Schema(description = "是否导出资产用例标识")
        boolean assetCaseIdentifierExported,
        @Schema(description = "是否导出 sourceRef")
        boolean sourceRefExported,
        @Schema(description = "是否导出错误明细")
        boolean errorDetailExported,
        @Schema(description = "是否为聚合输出")
        boolean aggregateOnly,
        @Schema(description = "运行手册步骤")
        List<TestDesignAuditChainReadinessResponse> steps,
        @Schema(description = "生成时间")
        Instant generatedAt
) {
}
