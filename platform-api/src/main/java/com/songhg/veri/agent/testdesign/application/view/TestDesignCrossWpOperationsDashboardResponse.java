package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Unified WP5 cross-WP operations dashboard response.
 */
public record TestDesignCrossWpOperationsDashboardResponse(
        @Schema(description = "所属项目 ID")
        String projectId,
        @Schema(description = "Prompt 模板标识过滤条件")
        String promptKey,
        @Schema(description = "作用域策略")
        TestDesignScopePolicyResponse scopePolicy,
        @Schema(description = "审计链策略")
        TestDesignAuditChainPolicyResponse auditChainPolicy,
        @Schema(description = "任务数量")
        long taskCount,
        @Schema(description = "候选数量")
        long candidateCount,
        @Schema(description = "发布记录数量")
        long publishRecordCount,
        @Schema(description = "项目作用域桶数量")
        long projectBucketCount,
        @Schema(description = "候选项目 scope 不一致数量")
        long candidateScopeMismatchCount,
        @Schema(description = "发布项目 scope 不一致数量")
        long publishScopeMismatchCount,
        @Schema(description = "模型调用引用数量")
        long modelInvocationReferenceCount,
        @Schema(description = "发布记录带项目 scope 数量")
        long publishProjectScopeRecordCount,
        @Schema(description = "候选项目 scope 覆盖率")
        double candidateScopeCoveragePercent,
        @Schema(description = "发布项目 scope 覆盖率")
        double publishScopeCoveragePercent,
        @Schema(description = "跨 WP 审计链聚合")
        TestDesignCrossWpAuditDashboardResponse auditDashboard,
        @Schema(description = "audit outbox 聚合和重放能力")
        TestDesignAuditOutboxOperationsResponse auditOutbox,
        @Schema(description = "运营指标")
        List<TestDesignAuditChainMetricResponse> metrics,
        @Schema(description = "准入状态")
        List<TestDesignAuditChainReadinessResponse> readiness,
        @Schema(description = "是否为聚合输出")
        boolean aggregateOnly,
        @Schema(description = "是否导出明细标识")
        boolean detailIdentifiersExported,
        @Schema(description = "生成时间")
        Instant generatedAt
) {
}
