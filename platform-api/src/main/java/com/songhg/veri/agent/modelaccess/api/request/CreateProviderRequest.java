package com.songhg.veri.agent.modelaccess.api.request;

import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateProviderRequest(
        @Schema(description = "名称，用于列表展示和人工识别")
        @NotBlank String name,
        @Schema(description = "供应商类型")
        @NotNull ProviderType providerType,
        @Schema(description = "模型路由分组")
        String routingGroup,
        @Schema(description = "模型能力列表")
        String capabilities,
        @Schema(description = "模型供应商基础地址")
        String baseUrl,
        @Schema(description = "模型供应商 API Key 的密钥引用")
        String apiKeyRef,
        @Schema(description = "优先级")
        @Min(0) Integer priority,
        @Schema(description = "模型调用超时时间，单位毫秒")
        @Min(100) Integer timeoutMs,
        @Schema(description = "每千输入 token 成本")
        @DecimalMin("0.0") BigDecimal inputCostPer1kTokens,
        @Schema(description = "每千输出 token 成本")
        @DecimalMin("0.0") BigDecimal outputCostPer1kTokens
) {
}
