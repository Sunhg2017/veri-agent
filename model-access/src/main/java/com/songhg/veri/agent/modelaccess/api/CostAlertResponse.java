package com.songhg.veri.agent.modelaccess.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record CostAlertResponse(
        String scope,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("period_start") String periodStart,
        @JsonProperty("period_end") String periodEnd,
        @JsonProperty("spent_cost") BigDecimal spentCost,
        @JsonProperty("budget_limit") BigDecimal budgetLimit,
        @JsonProperty("usage_ratio") BigDecimal usageRatio,
        String level,
        String message
) {
}
