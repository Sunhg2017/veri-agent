package com.songhg.veri.agent.modelaccess.application.view;

import java.time.Instant;
import java.util.UUID;

/**
 * Application result for provider circuit, rate-limit and concurrency state.
 */
public record ProviderResilienceResult(
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
