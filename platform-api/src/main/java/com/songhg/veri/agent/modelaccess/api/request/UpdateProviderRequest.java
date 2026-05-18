package com.songhg.veri.agent.modelaccess.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

public record UpdateProviderRequest(
        String name,
        String baseUrl,
        String apiKeyRef,
        @Min(0) Integer priority,
        @Min(100) Integer timeoutMs,
        @DecimalMin("0.0") BigDecimal inputCostPer1kTokens,
        @DecimalMin("0.0") BigDecimal outputCostPer1kTokens
) {
}
