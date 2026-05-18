package com.songhg.veri.agent.modelaccess.api.response;

import java.math.BigDecimal;

public record CostAlertResponse(
        String scope,
        String projectId,
        String periodStart,
        String periodEnd,
        BigDecimal spentCost,
        BigDecimal budgetLimit,
        BigDecimal usageRatio,
        String level,
        String message
) {
}
