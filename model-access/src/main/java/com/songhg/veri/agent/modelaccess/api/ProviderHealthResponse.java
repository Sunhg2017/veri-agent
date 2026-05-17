package com.songhg.veri.agent.modelaccess.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProviderHealthResponse(
        String service,
        String status,
        @JsonProperty("enabled_providers") int enabledProviders,
        @JsonProperty("active_prompts") int activePrompts
) {
}
