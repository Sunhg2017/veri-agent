package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Aggregate-only model observation drilldown.
 */
public record TestDesignModelObservationDrilldownResponse(
        @Schema(description = "所属项目 ID")
        String projectId,
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "模型调用总数")
        long totalInvocationCount,
        @Schema(description = "成功调用数")
        long succeededCount,
        @Schema(description = "失败调用数")
        long failedCount,
        @Schema(description = "预算或策略阻断数")
        long blockedCount,
        @Schema(description = "fallback 调用数")
        long fallbackCount,
        @Schema(description = "输入 token 总数")
        long inputTokenTotal,
        @Schema(description = "输出 token 总数")
        long outputTokenTotal,
        @Schema(description = "耗时总数，毫秒")
        long latencyMsTotal,
        @Schema(description = "平均耗时，毫秒")
        long averageLatencyMs,
        @Schema(description = "成本总数文本")
        String totalCostText,
        @Schema(description = "trace 信号数")
        long traceSignalCount,
        @Schema(description = "job 信号数")
        long jobSignalCount,
        @Schema(description = "聚合钻取桶")
        List<TestDesignModelObservationBucketResponse> buckets,
        @Schema(description = "是否支持钻取")
        boolean drilldownSupported,
        @Schema(description = "是否导出 traceId 原值")
        boolean traceIdValueExported,
        @Schema(description = "是否导出 jobId 原值")
        boolean jobIdValueExported,
        @Schema(description = "是否导出模型调用 ID 原值")
        boolean invocationIdValueExported,
        @Schema(description = "是否导出载荷预览")
        boolean payloadPreviewExported,
        @Schema(description = "是否导出 provider 错误正文")
        boolean providerErrorTextExported,
        @Schema(description = "是否为聚合输出")
        boolean aggregateOnly,
        @Schema(description = "生成时间")
        Instant generatedAt
) {
}
