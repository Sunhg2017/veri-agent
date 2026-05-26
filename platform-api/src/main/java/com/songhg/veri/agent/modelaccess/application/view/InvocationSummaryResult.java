package com.songhg.veri.agent.modelaccess.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Aggregated invocation counters used by budget checks and API projections.
 */
public record InvocationSummaryResult(
        @Schema(description = "本次处理总数。")
        long total,
        @Schema(description = "成功数量。")
        long succeeded,
        @Schema(description = "失败数量。")
        long failed,
        @Schema(description = "被阻断数量。")
        long blocked,
        @Schema(description = "输入 token 数。")
        long inputTokens,
        @Schema(description = "输出 token 数。")
        long outputTokens,
        @Schema(description = "总成本。")
        BigDecimal totalCost
) {
}
