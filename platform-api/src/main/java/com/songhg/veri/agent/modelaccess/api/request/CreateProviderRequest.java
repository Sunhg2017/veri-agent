package com.songhg.veri.agent.modelaccess.api.request;

import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateProviderRequest(
        @NotBlank String name,
        @NotNull ProviderType providerType,
        String routingGroup,
        String capabilities,
        String baseUrl,
        String apiKeyRef,
        @Min(0) Integer priority,
        @Min(100) Integer timeoutMs,
        @DecimalMin("0.0") BigDecimal inputCostPer1kTokens,
        @DecimalMin("0.0") BigDecimal outputCostPer1kTokens
) {
}
