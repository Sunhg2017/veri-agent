package com.songhg.veri.agent.modelaccess.api.response;


public record ProviderHealthResponse(
        String service,
        String status,
        int enabledProviders,
        int activePrompts,
        boolean providerRateLimitEnabled,
        int providerRateLimitMaxRequests,
        long providerRateLimitWindowSeconds,
        boolean providerConcurrencyLimitEnabled,
        int providerMaxConcurrentRequests,
        int openCircuitProviders
) {
}
