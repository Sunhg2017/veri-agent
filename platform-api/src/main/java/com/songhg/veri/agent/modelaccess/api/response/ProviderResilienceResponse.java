package com.songhg.veri.agent.modelaccess.api.response;

import java.time.Instant;
import java.util.UUID;

public record ProviderResilienceResponse(
        UUID providerId,
        String providerName,
        boolean circuitOpen,
        int consecutiveFailures,
        Instant circuitOpenUntil,
        boolean rateLimitEnabled,
        int rateLimitMaxRequests,
        long rateLimitWindowSeconds,
        boolean concurrencyLimitEnabled,
        int maxConcurrentRequests,
        int availableConcurrentPermits
) {
}
