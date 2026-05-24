package com.songhg.veri.agent.modelaccess.application.view;

import java.math.BigDecimal;

/**
 * Application result describing one budget alert scope.
 */
public record CostAlertResult(
        String scope,
        String projectId,
        String actorService,
        String periodStart,
        String periodEnd,
        BigDecimal spentCost,
        BigDecimal budgetLimit,
        BigDecimal usageRatio,
        String level,
        String message
) {
}
