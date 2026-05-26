package com.songhg.veri.agent.modelaccess.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProviderHealthResponse(
        @Schema(description = "服务标识。")
        String service,
        @Schema(description = "健康检查或连通性状态。")
        String status,
        @Schema(description = "已启用模型供应商数量。")
        int enabledProviders,
        @Schema(description = "已启用 Prompt 模板数量。")
        int activePrompts,
        @Schema(description = "供应商限流是否启用。")
        boolean providerRateLimitEnabled,
        @Schema(description = "供应商限流窗口内最大请求数。")
        int providerRateLimitMaxRequests,
        @Schema(description = "供应商限流窗口秒数。")
        long providerRateLimitWindowSeconds,
        @Schema(description = "供应商并发限制是否启用。")
        boolean providerConcurrencyLimitEnabled,
        @Schema(description = "供应商最大并发请求数。")
        int providerMaxConcurrentRequests,
        @Schema(description = "当前熔断打开的供应商数量。")
        int openCircuitProviders
) {
}
