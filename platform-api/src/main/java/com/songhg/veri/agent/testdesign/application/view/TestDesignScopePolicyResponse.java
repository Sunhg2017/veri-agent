package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 权限与资源作用域策略聚合快照。
 */
public record TestDesignScopePolicyResponse(
        @Schema(description = "作用域策略版本")
        String policyVersion,
        @Schema(description = "作用域模型")
        String scopeModel,
        @Schema(description = "默认列表查询作用域")
        String listFallbackScope,
        @Schema(description = "是否按任务项目校验任务级接口")
        boolean taskProjectScopeRequired,
        @Schema(description = "是否按候选项目校验候选级接口")
        boolean candidateProjectScopeRequired,
        @Schema(description = "是否按批量候选项目集合校验批量接口")
        boolean batchCandidateProjectScopeRequired,
        @Schema(description = "是否按任务项目校验发布接口")
        boolean publishProjectScopeRequired,
        @Schema(description = "异步生成是否从任务归属项目恢复作用域")
        boolean asyncTaskProjectScopeRecovered,
        @Schema(description = "HTTP smoke 是否使用项目作用域服务令牌链路")
        boolean smokeProjectScopeRequired,
        @Schema(description = "质量评测语料是否隔离在固定项目作用域")
        boolean evaluationCorpusProjectIsolated,
        @Schema(description = "是否支持评测语料运营后台")
        boolean evaluationCorpusOperationsReady,
        @Schema(description = "是否支持跨 WP 统一作用域看板")
        boolean crossWpScopeDashboardReady,
        @Schema(description = "是否导出候选 ID 列表")
        boolean candidateIdentifierListExported,
        @Schema(description = "是否导出角色规则明细")
        boolean roleRuleDetailExported,
        @Schema(description = "是否导出服务令牌原值")
        boolean serviceTokenValueExported,
        @Schema(description = "是否只暴露聚合作用域状态")
        boolean aggregateOnly
) {
}
