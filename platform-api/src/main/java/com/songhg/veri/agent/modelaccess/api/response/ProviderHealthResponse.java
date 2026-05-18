package com.songhg.veri.agent.modelaccess.api.response;


public record ProviderHealthResponse(
        String service,
        String status,
        int enabledProviders,
        int activePrompts
) {
}
