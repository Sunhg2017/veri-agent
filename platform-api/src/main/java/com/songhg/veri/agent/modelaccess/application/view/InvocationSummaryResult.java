package com.songhg.veri.agent.modelaccess.application.view;

import java.math.BigDecimal;

/**
 * Aggregated invocation counters used by budget checks and API projections.
 */
public record InvocationSummaryResult(
        long total,
        long succeeded,
        long failed,
        long blocked,
        long inputTokens,
        long outputTokens,
        BigDecimal totalCost
) {
}
