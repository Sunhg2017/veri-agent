package com.songhg.veri.agent.modelaccess.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ModelProviderConfig(
        UUID id,
        String name,
        @JsonProperty("provider_type")
        ProviderType providerType,
        @JsonProperty("base_url")
        String baseUrl,
        @JsonProperty("api_key_ref")
        String apiKeyRef,
        ProviderStatus status,
        int priority,
        @JsonProperty("timeout_ms")
        int timeoutMs,
        @JsonProperty("input_cost_per_1k_tokens")
        BigDecimal inputCostPer1kTokens,
        @JsonProperty("output_cost_per_1k_tokens")
        BigDecimal outputCostPer1kTokens,
        @JsonProperty("created_at")
        Instant createdAt,
        @JsonProperty("updated_at")
        Instant updatedAt
) {

    public boolean enabled() {
        return status == ProviderStatus.ENABLED;
    }
}
