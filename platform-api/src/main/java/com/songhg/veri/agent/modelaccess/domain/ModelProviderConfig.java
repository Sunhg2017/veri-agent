package com.songhg.veri.agent.modelaccess.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ModelProviderConfig(
        UUID id,
        String name,
        ProviderType providerType,
        String routingGroup,
        String capabilities,
        String baseUrl,
        String apiKeyRef,
        ProviderStatus status,
        int priority,
        int timeoutMs,
        BigDecimal inputCostPer1kTokens,
        BigDecimal outputCostPer1kTokens,
        Instant createdAt,
        Instant updatedAt
) {

    public boolean enabled() {
        return status == ProviderStatus.ENABLED;
    }
}
