package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 模型观测策略聚合快照。
 */
public record TestDesignModelObservationPolicyResponse(
        @Schema(description = "模型观测策略版本")
        String policyVersion,
        @Schema(description = "模型观测模式")
        String observationMode,
        @Schema(description = "是否跟踪 WP2 调用引用")
        boolean wp2InvocationReferenceTracked,
        @Schema(description = "是否跟踪 trace 信号")
        boolean traceIdTracked,
        @Schema(description = "是否跟踪异步 job 信号")
        boolean jobIdTracked,
        @Schema(description = "是否跟踪路由元数据")
        boolean routingMetadataTracked,
        @Schema(description = "是否跟踪 token 用量")
        boolean tokenUsageTracked,
        @Schema(description = "是否跟踪调用耗时")
        boolean latencyTracked,
        @Schema(description = "是否跟踪调用成本")
        boolean costTracked,
        @Schema(description = "是否跟踪 fallback 状态")
        boolean fallbackTracked,
        @Schema(description = "是否持久化 Prompt 载荷")
        boolean promptPayloadStored,
        @Schema(description = "是否导出模型载荷预览")
        boolean payloadPreviewExported,
        @Schema(description = "是否导出 traceId 原值")
        boolean traceIdValueExported,
        @Schema(description = "是否导出 jobId 原值")
        boolean jobIdValueExported,
        @Schema(description = "是否导出模型调用 ID 原值")
        boolean invocationIdValueExported,
        @Schema(description = "是否导出 provider 错误正文")
        boolean providerErrorTextExported,
        @Schema(description = "是否导出 actor service 原值")
        boolean actorServiceExported,
        @Schema(description = "是否只暴露聚合状态")
        boolean aggregateOnly
) {
}
