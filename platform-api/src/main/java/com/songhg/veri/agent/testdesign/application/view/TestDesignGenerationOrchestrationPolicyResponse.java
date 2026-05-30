package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 生成任务编排策略和运行态聚合快照。
 */
public record TestDesignGenerationOrchestrationPolicyResponse(
        @Schema(description = "编排策略版本")
        String policyVersion,
        @Schema(description = "生成编排模式")
        String orchestrationMode,
        @Schema(description = "是否启用异步生成")
        boolean asyncGenerationEnabled,
        @Schema(description = "是否支持 QUEUED -> RUNNING 条件认领")
        boolean conditionalRunClaimSupported,
        @Schema(description = "是否支持创建任务幂等回放")
        boolean idempotentCreateReplaySupported,
        @Schema(description = "重复生成事件是否安全")
        boolean duplicateEventReplaySafe,
        @Schema(description = "是否启用事件恢复扫描")
        boolean eventRecoveryEnabled,
        @Schema(description = "是否支持排队事件恢复重发")
        boolean queuedEventReplaySupported,
        @Schema(description = "是否启用运行中任务超时回收")
        boolean runningTimeoutRecoveryEnabled,
        @Schema(description = "运行中超时后是否必须显式重试")
        boolean explicitRetryRequiredAfterTimeout,
        @Schema(description = "是否支持人工任务重试")
        boolean manualTaskRetrySupported,
        @Schema(description = "人工排队事件重发入口是否就绪")
        boolean manualQueuedEventReplayReady,
        @Schema(description = "是否输出队列滞留聚合指标")
        boolean queueLagMetricReady,
        @Schema(description = "是否输出超时聚合告警")
        boolean timeoutAlertReady,
        @Schema(description = "多实例压测证据是否就绪")
        boolean multiInstanceLoadTestEvidenceReady,
        @Schema(description = "是否导出事件 payload")
        boolean eventPayloadExported,
        @Schema(description = "是否导出事件 ID 清单")
        boolean eventIdentifierListExported,
        @Schema(description = "是否导出队列消息体")
        boolean queueMessageBodyExported,
        @Schema(description = "是否导出恢复明细行")
        boolean recoveryDetailRowsExported,
        @Schema(description = "本次生效恢复批次上限")
        int effectiveRecoveryBatchSize,
        @Schema(description = "运行中任务超时秒数")
        long runningTimeoutSeconds,
        @Schema(description = "排队滞留告警秒数")
        long queueLagWarningSeconds,
        @Schema(description = "排队任务聚合计数")
        long queuedTaskCount,
        @Schema(description = "运行中任务聚合计数")
        long runningTaskCount,
        @Schema(description = "最旧排队任务年龄秒数")
        long oldestQueuedAgeSeconds,
        @Schema(description = "已达到运行超时阈值的运行中任务聚合计数")
        long staleRunningTaskCount,
        @Schema(description = "是否触发队列滞留告警")
        boolean queueLagWarning,
        @Schema(description = "是否触发运行超时告警")
        boolean timeoutWarning,
        @Schema(description = "当前任务是否处于排队状态")
        long queuedStatusSignal,
        @Schema(description = "当前任务是否处于运行中状态")
        long runningStatusSignal,
        @Schema(description = "当前任务是否为超时失败")
        long timeoutFailureSignal,
        @Schema(description = "是否只暴露聚合状态")
        boolean aggregateOnly
) {
}
