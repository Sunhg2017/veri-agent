package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cross-WP audit-chain counters used by the WP5 operations cockpit.
 */
public record TestDesignCrossWpAuditDashboardResponse(
        @Schema(description = "WP1 审计事件总数")
        long wp1AuditEventCount,
        @Schema(description = "WP1 审计成功数")
        long wp1AuditSuccessCount,
        @Schema(description = "WP1 审计失败数")
        long wp1AuditFailureCount,
        @Schema(description = "WP1 审计拒绝数")
        long wp1AuditDeniedCount,
        @Schema(description = "WP2 调用总数")
        long wp2InvocationCount,
        @Schema(description = "WP2 调用成功数")
        long wp2InvocationSucceededCount,
        @Schema(description = "WP2 调用失败数")
        long wp2InvocationFailedCount,
        @Schema(description = "WP2 调用阻断数")
        long wp2InvocationBlockedCount,
        @Schema(description = "WP2 fallback 数")
        long wp2FallbackCount,
        @Schema(description = "WP2 trace 信号数")
        long wp2TraceSignalCount,
        @Schema(description = "WP3 已发布用例数")
        long wp3PublishedCaseCount,
        @Schema(description = "WP3 追踪链接数")
        long wp3TraceLinkCount,
        @Schema(description = "是否已追踪跨 WP 审计链")
        boolean crossWpAuditDashboardReady,
        @Schema(description = "是否导出审计事件明细")
        boolean auditEventDetailExported,
        @Schema(description = "是否导出 traceId 原值")
        boolean traceIdValueExported,
        @Schema(description = "是否导出模型调用 ID 原值")
        boolean modelInvocationIdValueExported,
        @Schema(description = "是否导出发布 sourceRef 或资产 ID 原值")
        boolean publishIdentifierValueExported,
        @Schema(description = "是否为聚合输出")
        boolean aggregateOnly
) {
}
