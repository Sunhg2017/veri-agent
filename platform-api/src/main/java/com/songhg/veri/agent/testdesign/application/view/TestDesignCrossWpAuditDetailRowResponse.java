package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Redacted cross-WP audit detail row.
 */
public record TestDesignCrossWpAuditDetailRowResponse(
        @Schema(description = "跨 WP 分区")
        String section,
        @Schema(description = "分类")
        String category,
        @Schema(description = "状态")
        String status,
        @Schema(description = "事件数")
        long eventCount,
        @Schema(description = "成功数")
        long successCount,
        @Schema(description = "失败数")
        long failedCount,
        @Schema(description = "告警数")
        long warningCount,
        @Schema(description = "最近事件时间")
        Instant latestEventAt,
        @Schema(description = "是否导出标识原值")
        boolean identifierValuesExported,
        @Schema(description = "是否导出 payload 或正文")
        boolean payloadExported,
        @Schema(description = "是否导出操作者标识")
        boolean actorIdentifierExported,
        @Schema(description = "是否为聚合输出")
        boolean aggregateOnly
) {
}
