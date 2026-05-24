package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.domain.ProviderStatus;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import java.time.Instant;
import java.util.UUID;

/**
 * Application result for provider readiness checks.
 */
public record ProviderCheckResult(
        UUID providerId,
        String providerName,
        ProviderType providerType,
        ProviderStatus providerStatus,
        String status,
        long latencyMs,
        String modelName,
        String errorCode,
        String errorMessage,
        boolean cached,
        Instant checkedAt
) {
}
