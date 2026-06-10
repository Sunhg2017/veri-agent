package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Aggregate-only WP1 audit outbox operations status for WP5 cross-WP governance.
 */
public record TestDesignAuditOutboxOperationsResponse(
        @Schema(description = "outbox 总数")
        long totalCount,
        @Schema(description = "待处理数量")
        long pendingCount,
        @Schema(description = "处理中数量")
        long processingCount,
        @Schema(description = "已完成数量")
        long doneCount,
        @Schema(description = "失败数量")
        long failedCount,
        @Schema(description = "死亡信箱数量")
        long deadCount,
        @Schema(description = "可重新排队数量")
        long replayEligibleCount,
        @Schema(description = "是否支持受限重新排队")
        boolean replaySupported,
        @Schema(description = "是否导出 outbox payload")
        boolean payloadExported,
        @Schema(description = "是否导出 traceId 原值")
        boolean traceIdValueExported,
        @Schema(description = "是否导出最近错误正文")
        boolean lastErrorTextExported,
        @Schema(description = "是否为聚合输出")
        boolean aggregateOnly
) {
}
