package com.songhg.veri.agent.modelaccess.api.response;

import java.math.BigDecimal;

public record InvocationSummaryResponse(
        long total,
        long succeeded,
        long failed,
        long blocked,
        long inputTokens,
        long outputTokens,
        BigDecimal totalCost
) {
}
