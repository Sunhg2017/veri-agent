package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import java.math.BigDecimal;

/**
 * Application command for registering a model provider.
 */
public record CreateProviderCommand(
        String name,
        ProviderType providerType,
        String routingGroup,
        String capabilities,
        String baseUrl,
        String apiKeyRef,
        Integer priority,
        Integer timeoutMs,
        BigDecimal inputCostPer1kTokens,
        BigDecimal outputCostPer1kTokens
) {
}
