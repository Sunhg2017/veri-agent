package com.songhg.veri.agent.modelaccess.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ProviderResilienceResponse(
        @Schema(description = "模型供应商 ID")
        UUID providerId,
        @Schema(description = "模型供应商名称")
        String providerName,
        @Schema(description = "熔断器是否打开")
        boolean circuitOpen,
        @Schema(description = "连续失败次数")
        int consecutiveFailures,
        @Schema(description = "熔断持续到的时间")
        Instant circuitOpenUntil,
        @Schema(description = "限流是否启用")
        boolean rateLimitEnabled,
        @Schema(description = "限流窗口内最大请求数")
        int rateLimitMaxRequests,
        @Schema(description = "限流窗口秒数")
        long rateLimitWindowSeconds,
        @Schema(description = "并发限制是否启用")
        boolean concurrencyLimitEnabled,
        @Schema(description = "最大并发请求数")
        int maxConcurrentRequests,
        @Schema(description = "当前可用并发许可数")
        int availableConcurrentPermits
) {
}
