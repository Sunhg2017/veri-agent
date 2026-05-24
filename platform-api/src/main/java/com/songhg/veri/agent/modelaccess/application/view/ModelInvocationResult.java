package com.songhg.veri.agent.modelaccess.application.view;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Application-layer result for a model invocation.
 *
 * <p>External response DTOs are derived from this result in the API layer, keeping invocation
 * orchestration free of presentation concerns.</p>
 */
public record ModelInvocationResult(
        UUID invocationId,
        UUID providerId,
        String providerName,
        String modelName,
        boolean fallbackUsed,
        String content,
        int inputTokens,
        int outputTokens,
        BigDecimal totalCost
) {
}
