package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One aggregate bucket in model observation drilldown.
 */
public record TestDesignModelObservationBucketResponse(
        @Schema(description = "钻取维度")
        String dimension,
        @Schema(description = "桶编码")
        String bucketKey,
        @Schema(description = "桶名称")
        String bucketLabel,
        @Schema(description = "调用数")
        long invocationCount,
        @Schema(description = "成功数")
        long succeededCount,
        @Schema(description = "失败数")
        long failedCount,
        @Schema(description = "阻断数")
        long blockedCount,
        @Schema(description = "fallback 数")
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
        @Schema(description = "最近一次调用时间")
        Instant latestInvocationAt
) {
}
