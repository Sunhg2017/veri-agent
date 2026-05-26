package com.songhg.veri.agent.document.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "veri-agent.document-input")
public record DocumentInputProperties(
        /** 文档输入模块内部调用令牌 */
        String serviceToken,
        /** 默认 webhook 签名密钥 */
        String webhookSecret,
        /** webhook 签名时间戳允许偏移秒数 */
        long webhookClockSkewSeconds,
        /** 是否启用文档输入能力 */
        boolean inputEnabled,
        /** 是否启用 webhook 接入能力 */
        boolean webhookEnabled,
        /** 是否启用模型解析能力 */
        boolean modelParseEnabled,
        /** 模型解析使用的提示词模板键 */
        String modelParsePromptKey,
        /** 模型解析请求的敏感级别 */
        String modelParseSensitivityLevel,
        /** 模型解析是否允许使用公共模型 */
        boolean modelParseAllowPublicModel,
        /** 模型解析允许输入的最大文本长度 */
        int modelParseMaxContentChars,
        /** 导入内容最大字节数 */
        long importMaxContentBytes,
        /** 二进制文档最大字节数 */
        long documentBinaryMaxBytes,
        /** 本地 OCR 命令 */
        String ocrCommand,
        /** OCR 执行超时时间，单位秒 */
        int ocrTimeoutSeconds,
        /** OCR 输出最大字符数 */
        int ocrMaxOutputChars,
        /** OCR 本地进程最大并发数 */
        int ocrMaxConcurrentProcesses,
        /** SecretProvider 不可用时是否允许回退到本地 webhook 密钥 */
        boolean localWebhookSecretFallbackEnabled,
        /** webhook 请求体最大字节数 */
        long webhookMaxPayloadBytes,
        /** 候选批量操作最大条数 */
        int batchActionLimit,
        /** webhook 事件最大重放次数 */
        int webhookMaxReplayAttempts,
        /** 是否启用 webhook 自动重试 */
        boolean webhookAutoRetryEnabled,
        /** webhook 自动重试单批处理数量 */
        int webhookAutoRetryBatchSize,
        /** webhook 密钥解析缓存 TTL，单位秒 */
        long webhookSecretCacheTtlSeconds,
        /** webhook 密钥轮换重叠窗口，单位秒 */
        long webhookSecretRotationOverlapSeconds,
        /** 按来源配置的 webhook 签名密钥 */
        @DefaultValue Map<String, String> webhookSecrets,
        /** 全局 webhook IP/CIDR 白名单 */
        String webhookAllowedCidrs,
        /** 按来源配置的 webhook IP/CIDR 白名单 */
        Map<String, String> webhookSourceAllowedCidrs,
        /** 可信代理 IP/CIDR 列表 */
        String webhookTrustedProxyCidrs,
        /** webhook 限流窗口内最大请求数 */
        int webhookRateLimitMaxRequests,
        /** webhook 限流窗口秒数 */
        long webhookRateLimitWindowSeconds,
        /** 是否启用二进制 MIME 类型校验 */
        @DefaultValue("true") boolean binaryMimeValidationEnabled,
        /** PDF 最大解析页数 */
        int pdfMaxPages,
        /** PDF 最大解析耗时，单位毫秒 */
        long pdfMaxParseMillis,
        /** OCR worker 模式，如 LOCAL_COMMAND 或 REMOTE */
        @DefaultValue("LOCAL_COMMAND") String ocrWorkerMode,
        /** 远程 OCR worker 地址 */
        String ocrWorkerUrl,
        /** 远程 OCR worker 认证令牌 */
        String ocrWorkerToken,
        /** 远程 OCR 失败时是否允许回退到本地命令 */
        @DefaultValue("true") boolean ocrLocalCommandFallbackEnabled,
        /** 恶意文件扫描命令 */
        String malwareScanCommand,
        /** 恶意文件扫描超时时间，单位秒 */
        int malwareScanTimeoutSeconds,
        /** 恶意文件扫描最大并发进程数 */
        int malwareScanMaxConcurrentProcesses,
        /** 恶意文件扫描输出最大字符数 */
        int malwareScanMaxOutputChars,
        /** 是否启用文档输入保留清理任务 */
        boolean retentionCleanupEnabled,
        /** 导入记录保留天数 */
        int importRetentionDays,
        /** webhook 事件保留天数 */
        int webhookEventRetentionDays
) {
}
