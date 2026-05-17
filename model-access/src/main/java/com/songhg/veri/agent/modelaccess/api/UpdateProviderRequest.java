package com.songhg.veri.agent.modelaccess.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

public record UpdateProviderRequest(
        String name,
        @JsonProperty("base_url") String baseUrl,
        @JsonProperty("api_key_ref") String apiKeyRef,
        @Min(0) Integer priority,
        @JsonProperty("timeout_ms") @Min(100) Integer timeoutMs,
        @JsonProperty("input_cost_per_1k_tokens") @DecimalMin("0.0") BigDecimal inputCostPer1kTokens,
        @JsonProperty("output_cost_per_1k_tokens") @DecimalMin("0.0") BigDecimal outputCostPer1kTokens
) {
}
