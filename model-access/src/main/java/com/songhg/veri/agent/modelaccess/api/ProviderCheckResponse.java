package com.songhg.veri.agent.modelaccess.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.songhg.veri.agent.modelaccess.domain.ProviderStatus;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import java.time.Instant;
import java.util.UUID;

public record ProviderCheckResponse(
        @JsonProperty("provider_id") UUID providerId,
        @JsonProperty("provider_name") String providerName,
        @JsonProperty("provider_type") ProviderType providerType,
        @JsonProperty("provider_status") ProviderStatus providerStatus,
        String status,
        @JsonProperty("latency_ms") long latencyMs,
        @JsonProperty("model_name") String modelName,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("cached") boolean cached,
        @JsonProperty("checked_at") Instant checkedAt
) {
}
