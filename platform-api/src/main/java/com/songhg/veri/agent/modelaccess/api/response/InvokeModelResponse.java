package com.songhg.veri.agent.modelaccess.api.response;

import java.math.BigDecimal;
import java.util.UUID;

public record InvokeModelResponse(
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
