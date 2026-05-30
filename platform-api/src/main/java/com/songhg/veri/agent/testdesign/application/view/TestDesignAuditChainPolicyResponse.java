package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 跨 WP 审计链可观测策略聚合快照。
 */
public record TestDesignAuditChainPolicyResponse(
        @Schema(description = "审计链策略版本")
        String policyVersion,
        @Schema(description = "审计链模式")
        String chainMode,
        @Schema(description = "审计事件来源")
        String eventSource,
        @Schema(description = "是否写入 WP1 审计事件")
        boolean wp1AuditEventWritten,
        @Schema(description = "是否跟踪 WP2 模型调用引用")
        boolean wp2InvocationReferenceTracked,
        @Schema(description = "是否跟踪 WP3 发布引用")
        boolean wp3PublishReferenceTracked,
        @Schema(description = "是否跟踪 WP5 任务本域事件")
        boolean wp5DomainEventsTracked,
        @Schema(description = "是否按项目作用域隔离")
        boolean projectScopeRequired,
        @Schema(description = "是否保留 traceId 追踪信号")
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
        @Schema(description = "跨 WP1/WP2/WP3 统一审计看板是否就绪")
        boolean crossWpAuditDashboardReady,
        @Schema(description = "审计 outbox 重放看板是否就绪")
        boolean auditOutboxReplayDashboardReady,
        @Schema(description = "是否只暴露聚合状态")
        boolean aggregateOnly
) {
}
