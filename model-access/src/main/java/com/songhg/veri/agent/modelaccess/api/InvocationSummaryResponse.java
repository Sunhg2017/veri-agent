package com.songhg.veri.agent.modelaccess.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record InvocationSummaryResponse(
        long total,
        long succeeded,
        long failed,
        long blocked,
        @JsonProperty("input_tokens") long inputTokens,
        @JsonProperty("output_tokens") long outputTokens,
        @JsonProperty("total_cost") BigDecimal totalCost
) {
}
