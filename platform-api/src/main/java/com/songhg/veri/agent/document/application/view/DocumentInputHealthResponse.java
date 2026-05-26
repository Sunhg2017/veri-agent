package com.songhg.veri.agent.document.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

public record DocumentInputHealthResponse(
        @Schema(description = "服务标识。")
        String service,
        @Schema(description = "健康检查或连通性状态。")
        String status,
        @Schema(description = "支持的文档来源类型数量。")
        int supportedSourceTypes,
        @Schema(description = "是否启用文档输入能力。")
        boolean inputEnabled,
        @Schema(description = "是否启用 Webhook 接入。")
        boolean webhookEnabled,
        @Schema(description = "是否启用模型解析。")
        boolean modelParseEnabled,
        @Schema(description = "Webhook 最大请求体字节数。")
        long webhookMaxPayloadBytes,
        @Schema(description = "手工导入最大正文字节数。")
        long importMaxContentBytes,
        @Schema(description = "二进制文档最大字节数。")
        long documentBinaryMaxBytes,
        @Schema(description = "OCR 是否已配置。")
        boolean ocrConfigured,
        @Schema(description = "OCR 单次处理超时时间，单位秒。")
        int ocrTimeoutSeconds,
        @Schema(description = "OCR 最大输出字符数。")
        int ocrMaxOutputChars,
        @Schema(description = "OCR 最大并发进程数。")
        int ocrMaxConcurrentProcesses,
        @Schema(description = "OCR 当前可用并发许可数。")
        int ocrAvailablePermits,
        @Schema(description = "OCR worker 模式。")
        String ocrWorkerMode,
        @Schema(description = "是否配置远程 OCR worker。")
        boolean ocrRemoteWorkerConfigured,
        @Schema(description = "是否配置 OCR worker 令牌。")
        boolean ocrWorkerTokenConfigured,
        @Schema(description = "是否允许本地命令 OCR fallback。")
        boolean ocrLocalCommandFallbackEnabled,
        @Schema(description = "当前环境是否允许执行本地 OCR 命令。")
        boolean ocrLocalCommandExecutionAllowed,
        @Schema(description = "单次批量操作数量上限。")
        int batchActionLimit,
        @Schema(description = "Webhook IP 白名单是否启用。")
        boolean webhookIpAllowlistEnabled,
        @Schema(description = "是否配置可信代理 CIDR。")
        boolean webhookTrustedProxyCidrsConfigured,
        @Schema(description = "Webhook 限流是否启用。")
        boolean webhookRateLimitEnabled,
        @Schema(description = "Webhook 限流窗口内最大请求数。")
        int webhookRateLimitMaxRequests,
        @Schema(description = "Webhook 限流窗口秒数。")
        long webhookRateLimitWindowSeconds,
        @Schema(description = "二进制文档 MIME 校验是否启用。")
        boolean binaryMimeValidationEnabled,
        @Schema(description = "PDF 最大页数。")
        int pdfMaxPages,
        @Schema(description = "PDF 最大解析耗时，单位毫秒。")
        long pdfMaxParseMillis,
        @Schema(description = "恶意文件扫描是否启用。")
        boolean malwareScanEnabled,
        @Schema(description = "恶意文件扫描超时时间，单位秒。")
        int malwareScanTimeoutSeconds,
        @Schema(description = "恶意文件扫描最大并发进程数。")
        int malwareScanMaxConcurrentProcesses,
        @Schema(description = "恶意文件扫描当前可用并发许可数。")
        int malwareScanAvailablePermits,
        @Schema(description = "Webhook 密钥缓存是否启用。")
        boolean webhookSecretCacheEnabled,
        @Schema(description = "Webhook 密钥缓存 TTL，单位秒。")
        long webhookSecretCacheTtlSeconds,
        @Schema(description = "Webhook 密钥轮换重叠窗口，单位秒。")
        long webhookSecretRotationOverlapSeconds,
        @Schema(description = "Webhook 密钥缓存容量。")
        int webhookSecretCacheSize,
        @Schema(description = "外部密钥供应商健康状态。")
        DocumentSecretProviderHealthResponse externalSecretProvider
) {
}
