package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * WP5 服务健康和生成能力接口出参
 */
public record TestDesignHealthResponse(
        @Schema(description = "服务标识")
        String service,
        @Schema(description = "健康检查或连通性状态")
        String status,
        @Schema(description = "是否允许创建生成任务")
        boolean generationEnabled,
        @Schema(description = "生成模式")
        String generationMode,
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "Prompt 模板版本")
        String promptVersion,
        @Schema(description = "单任务最大需求数")
        int maxRequirementsPerTask,
        @Schema(description = "每个需求最大候选数")
        int maxCasesPerRequirement,
        @Schema(description = "上下文裁剪策略的生效上限")
        Map<String, Integer> contextLimits,
        @Schema(description = "上下文策略治理状态快照")
        TestDesignContextPolicyGovernanceResponse contextPolicyGovernance,
        @Schema(description = "上下文策略运营 v2 聚合快照")
        TestDesignContextPolicyOperationsResponse contextPolicyOperations,
        @Schema(description = "支持的覆盖类型列表")
        List<String> supportedCoverageTypes
) {
}
