package com.songhg.veri.agent.modelaccess.application.view;

import com.songhg.veri.agent.modelaccess.domain.ProviderStatus;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Application result for provider readiness checks.
 */
public record ProviderCheckResult(
        @Schema(description = "模型供应商 ID。")
        UUID providerId,
        @Schema(description = "模型供应商名称。")
        String providerName,
        @Schema(description = "供应商类型。")
        ProviderType providerType,
        @Schema(description = "供应商状态。")
        ProviderStatus providerStatus,
        @Schema(description = "健康检查或连通性状态。")
        String status,
        @Schema(description = "请求耗时，单位毫秒。")
        long latencyMs,
        @Schema(description = "模型名称。")
        String modelName,
        @Schema(description = "错误编码。")
        String errorCode,
        @Schema(description = "错误摘要。")
        String errorMessage,
        @Schema(description = "是否命中缓存。")
        boolean cached,
        @Schema(description = "检查时间。")
        Instant checkedAt
) {
}
