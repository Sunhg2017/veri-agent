package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * WP5 task-level cross-work-package audit-chain aggregate view.
 */
public record TestDesignAuditChainResponse(
        @Schema(description = "任务 ID")
        UUID taskId,
        @Schema(description = "所属项目 ID")
        String projectId,
        @Schema(description = "任务状态")
        String taskStatus,
        @Schema(description = "策略版本")
        String policyVersion,
        @Schema(description = "审计链模式")
        String chainMode,
        @Schema(description = "事件来源边界")
        String eventSource,
        @Schema(description = "是否已写 WP1 审计事件")
        boolean wp1AuditEventWritten,
        @Schema(description = "是否已追踪 WP2 调用引用")
        boolean wp2InvocationReferenceTracked,
        @Schema(description = "是否已追踪 WP3 发布引用")
        boolean wp3PublishReferenceTracked,
        @Schema(description = "是否已追踪 WP5 本域事件")
        boolean wp5DomainEventsTracked,
        @Schema(description = "是否要求项目作用域")
        boolean projectScopeRequired,
        @Schema(description = "是否保留 trace 信号")
        boolean traceSignalTracked,
        @Schema(description = "是否导出审计事件明细")
        boolean auditEventDetailExported,
        @Schema(description = "是否导出候选 ID 清单")
        boolean candidateIdentifierListExported,
        @Schema(description = "是否导出平台审计标识原值")
        boolean platformAuditIdentifierExported,
        @Schema(description = "是否导出 traceId 原值")
        boolean traceIdValueExported,
        @Schema(description = "是否导出模型调用 ID 原值")
        boolean modelInvocationIdValueExported,
        @Schema(description = "是否导出发布 sourceRef 或资产 ID 原值")
        boolean publishIdentifierValueExported,
        @Schema(description = "只读聚合看板骨架是否就绪")
        boolean readOnlyAggregateDashboardReady,
        @Schema(description = "完整跨 WP 审计看板是否就绪")
        boolean crossWpAuditDashboardReady,
        @Schema(description = "audit outbox 重放看板是否就绪")
        boolean auditOutboxReplayDashboardReady,
        @Schema(description = "是否为聚合输出")
        boolean aggregateOnly,
        @Schema(description = "WP5 本域事件总数")
        long wp5DomainEventCount,
        @Schema(description = "WP5 本域评审记录数")
        long wp5ReviewRecordCount,
        @Schema(description = "WP5 本域发布记录数")
        long wp5PublishRecordCount,
        @Schema(description = "WP5 本域失败或冲突记录数")
        long wp5IssueCount,
        @Schema(description = "WP5 本域说明覆盖记录数")
        long wp5NoteCoverageCount,
        @Schema(description = "跨 WP 聚合指标")
        List<TestDesignAuditChainMetricResponse> metrics,
        @Schema(description = "当前看板准入状态")
        List<TestDesignAuditChainReadinessResponse> readiness,
        @Schema(description = "生成时间")
        Instant generatedAt
) {
}
