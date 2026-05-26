package com.songhg.veri.agent.document.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record DocumentSecretProviderHealthResponse(
        @Schema(description = "密钥或模型供应商编码")
        String providerCode,
        @Schema(description = "供应商类型")
        String providerType,
        @Schema(description = "是否已完成配置")
        boolean configured,
        @Schema(description = "健康检查或连通性状态")
        String status,
        @Schema(description = "超时时间，单位秒")
        int timeoutSeconds,
        @Schema(description = "最大尝试次数")
        int maxAttempts,
        @Schema(description = "检查时间")
        Instant checkedAt,
        @Schema(description = "最近一次错误信息")
        String lastErrorMessage
) {
}
