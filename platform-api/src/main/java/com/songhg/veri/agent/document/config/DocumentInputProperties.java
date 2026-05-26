package com.songhg.veri.agent.document.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "veri-agent.document-input")
public record DocumentInputProperties(
        String serviceToken,
        String webhookSecret,
        long webhookClockSkewSeconds,
        boolean inputEnabled,
        boolean webhookEnabled,
        boolean modelParseEnabled,
        String modelParsePromptKey,
        String modelParseSensitivityLevel,
        boolean modelParseAllowPublicModel,
        int modelParseMaxContentChars,
        long importMaxContentBytes,
        long documentBinaryMaxBytes,
        String ocrCommand,
        int ocrTimeoutSeconds,
        int ocrMaxOutputChars,
        int ocrMaxConcurrentProcesses,
        boolean localWebhookSecretFallbackEnabled,
        long webhookMaxPayloadBytes,
        int batchActionLimit,
        int webhookMaxReplayAttempts,
        boolean webhookAutoRetryEnabled,
        int webhookAutoRetryBatchSize,
        long webhookSecretCacheTtlSeconds,
        long webhookSecretRotationOverlapSeconds,
        @DefaultValue Map<String, String> webhookSecrets,
        String webhookAllowedCidrs,
        Map<String, String> webhookSourceAllowedCidrs,
        String webhookTrustedProxyCidrs,
        int webhookRateLimitMaxRequests,
        long webhookRateLimitWindowSeconds,
        @DefaultValue("true") boolean binaryMimeValidationEnabled,
        int pdfMaxPages,
        long pdfMaxParseMillis,
        @DefaultValue("LOCAL_COMMAND") String ocrWorkerMode,
        String ocrWorkerUrl,
        String ocrWorkerToken,
        @DefaultValue("true") boolean ocrLocalCommandFallbackEnabled,
        String malwareScanCommand,
        int malwareScanTimeoutSeconds,
        int malwareScanMaxConcurrentProcesses,
        int malwareScanMaxOutputChars,
        boolean retentionCleanupEnabled,
        int importRetentionDays,
        int webhookEventRetentionDays
) {
}
