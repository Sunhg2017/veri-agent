package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Aggregate-only queue alert operations snapshot.
 */
public record TestDesignQueueAlertOperationsResponse(
        @Schema(description = "策略版本")
        String policyVersion,
        @Schema(description = "订阅总数")
        long subscriptionCount,
        @Schema(description = "启用订阅数量")
        long enabledSubscriptionCount,
        @Schema(description = "禁用订阅数量")
        long disabledSubscriptionCount,
        @Schema(description = "待消费生成任务数")
        long queuedTaskCount,
        @Schema(description = "运行超时生成任务数")
        long staleRunningTaskCount,
        @Schema(description = "待消费发布候选数")
        long publishQueuedCandidateCount,
        @Schema(description = "发布中超时候选数")
        long stalePublishingCandidateCount,
        @Schema(description = "可补偿候选数")
        long compensationEligibleCandidateCount,
        @Schema(description = "最老生成排队年龄秒数")
        long oldestGenerationQueuedAgeSeconds,
        @Schema(description = "最老发布排队年龄秒数")
        long oldestPublishQueuedAgeSeconds,
        @Schema(description = "生成队列滞留告警阈值秒数")
        long generationQueueLagWarningSeconds,
        @Schema(description = "发布队列滞留告警阈值秒数")
        long publishQueueLagWarningSeconds,
        @Schema(description = "生成队列是否滞留")
        boolean generationQueueLagWarning,
        @Schema(description = "生成运行是否超时")
        boolean generationTimeoutWarning,
        @Schema(description = "发布队列是否滞留")
        boolean publishQueueLagWarning,
        @Schema(description = "发布运行是否超时")
        boolean publishTimeoutWarning,
        @Schema(description = "是否存在待补偿候选")
        boolean compensationFailureWarning,
        @Schema(description = "启用订阅覆盖的活跃告警数")
        long activeWarningCount,
        @Schema(description = "订阅配置是否就绪")
        boolean subscriptionConfigReady,
        @Schema(description = "是否支持人工重放")
        boolean manualReplaySupported,
        @Schema(description = "是否为聚合输出")
        boolean aggregateOnly,
        @Schema(description = "是否导出事件 payload")
        boolean eventPayloadExported,
        @Schema(description = "是否导出任务/候选等明细标识")
        boolean detailIdentifiersExported,
        @Schema(description = "生成时间")
        Instant generatedAt
) {
}
