package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * WP5 permission and resource-scope operations summary with aggregate-only counters.
 */
public record TestDesignScopeSummaryResponse(
        @Schema(description = "所属项目 ID 过滤条件")
        String projectId,
        @Schema(description = "Prompt 模板标识过滤条件")
        String promptKey,
        @Schema(description = "权限与资源作用域策略快照")
        TestDesignScopePolicyResponse policy,
        @Schema(description = "纳入聚合的最近任务数量")
        long taskCount,
        @Schema(description = "纳入聚合的候选数量")
        long candidateCount,
        @Schema(description = "纳入聚合的发布记录数量")
        long publishRecordCount,
        @Schema(description = "按任务项目归属聚合的项目桶数量")
        long projectBucketCount,
        @Schema(description = "存在候选项目与任务项目不一致的候选数量")
        long candidateScopeMismatchCount,
        @Schema(description = "存在发布记录项目与任务项目不一致的记录数量")
        long publishScopeMismatchCount,
        @Schema(description = "具备模型调用引用的任务或候选数量")
        long modelInvocationReferenceCount,
        @Schema(description = "存在发布项目作用域的发布记录数量")
        long publishProjectScopeRecordCount,
        @Schema(description = "候选项目作用域完整率，百分比")
        double candidateScopeCoveragePercent,
        @Schema(description = "发布记录项目作用域完整率，百分比")
        double publishScopeCoveragePercent,
        @Schema(description = "作用域聚合指标")
        List<TestDesignAuditChainMetricResponse> metrics,
        @Schema(description = "作用域聚合准入状态")
        List<TestDesignAuditChainReadinessResponse> readiness,
        @Schema(description = "是否只暴露聚合状态")
        boolean aggregateOnly,
        @Schema(description = "是否导出候选 ID 列表")
        boolean candidateIdentifierListExported,
        @Schema(description = "是否导出角色规则明细")
        boolean roleRuleDetailExported,
        @Schema(description = "是否导出服务令牌原值")
        boolean serviceTokenValueExported,
        @Schema(description = "生成时间")
        Instant generatedAt
) {
}
