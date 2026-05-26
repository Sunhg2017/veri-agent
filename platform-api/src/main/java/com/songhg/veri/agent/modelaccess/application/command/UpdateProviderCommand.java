package com.songhg.veri.agent.modelaccess.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Application command for partially updating a model provider.
 */
public record UpdateProviderCommand(
        @Schema(description = "名称，用于列表展示和人工识别。")
        String name,
        @Schema(description = "模型路由分组。")
        String routingGroup,
        @Schema(description = "模型能力列表。")
        String capabilities,
        @Schema(description = "模型供应商基础地址。")
        String baseUrl,
        @Schema(description = "模型供应商 API Key 的密钥引用。")
        String apiKeyRef,
        @Schema(description = "优先级。")
        Integer priority,
        @Schema(description = "模型调用超时时间，单位毫秒。")
        Integer timeoutMs,
        @Schema(description = "每千输入 token 成本。")
        BigDecimal inputCostPer1kTokens,
        @Schema(description = "每千输出 token 成本。")
        BigDecimal outputCostPer1kTokens
) {
}
