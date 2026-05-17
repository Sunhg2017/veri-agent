package com.songhg.veri.agent.modelaccess.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record InvokeModelResponse(
        @JsonProperty("invocation_id") UUID invocationId,
        @JsonProperty("provider_id") UUID providerId,
        @JsonProperty("provider_name") String providerName,
        @JsonProperty("model_name") String modelName,
        @JsonProperty("fallback_used") boolean fallbackUsed,
        String content,
        @JsonProperty("input_tokens") int inputTokens,
        @JsonProperty("output_tokens") int outputTokens,
        @JsonProperty("total_cost") BigDecimal totalCost
) {
}
