package com.songhg.veri.agent.reporting.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReportingWebhookDeliveryHealthResponse(
        @Schema(description = "是否启用报告 webhook 回调")
        boolean enabled,
        @Schema(description = "是否已配置回调地址")
        boolean urlConfigured,
        @Schema(description = "脱敏后的回调地址")
        String callbackUrl,
        @Schema(description = "是否启用签名")
        boolean signatureEnabled,
        @Schema(description = "是否已配置签名 secretRef")
        boolean secretRefConfigured,
        @Schema(description = "签名 secretRef digest")
        String secretRefDigest,
        @Schema(description = "超时时间毫秒")
        int timeoutMs
) {
}
