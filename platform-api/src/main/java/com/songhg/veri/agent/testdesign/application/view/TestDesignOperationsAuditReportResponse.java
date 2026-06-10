package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Aggregate-only batch operations audit report.
 */
public record TestDesignOperationsAuditReportResponse(
        @Schema(description = "所属项目 ID")
        String projectId,
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "运营操作总数")
        long totalOperationCount,
        @Schema(description = "成功操作数")
        long successCount,
        @Schema(description = "失败操作数")
        long failedCount,
        @Schema(description = "拒绝操作数")
        long deniedCount,
        @Schema(description = "队列告警订阅变更数")
        long queueAlertSubscriptionMutationCount,
        @Schema(description = "queued event 重放次数")
        long queuedEventReplayCount,
        @Schema(description = "发布补偿人工运行次数")
        long publishCompensationRunCount,
        @Schema(description = "audit outbox 重排次数")
        long auditOutboxRequeueCount,
        @Schema(description = "最近一次操作时间")
        Instant latestOperationAt,
        @Schema(description = "是否支持导出聚合报表")
        boolean exportSupported,
        @Schema(description = "是否导出审计明细行")
        boolean detailRowsExported,
        @Schema(description = "是否导出操作者标识")
        boolean actorIdentifierExported,
        @Schema(description = "是否导出 traceId 值")
        boolean traceIdValueExported,
        @Schema(description = "是否为聚合输出")
        boolean aggregateOnly,
        @Schema(description = "生成时间")
        Instant generatedAt
) {
}
