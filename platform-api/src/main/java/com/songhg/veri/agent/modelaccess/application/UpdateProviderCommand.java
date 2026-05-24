package com.songhg.veri.agent.modelaccess.application;

import java.math.BigDecimal;

/**
 * Application command for partially updating a model provider.
 */
public record UpdateProviderCommand(
        String name,
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
