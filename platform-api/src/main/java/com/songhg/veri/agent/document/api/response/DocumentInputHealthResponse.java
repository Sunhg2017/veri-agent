package com.songhg.veri.agent.document.api.response;

import com.songhg.veri.agent.document.application.view.DocumentSecretProviderHealthResponse;

public record DocumentInputHealthResponse(
        String service,
        String status,
        int supportedSourceTypes,
        boolean inputEnabled,
        boolean webhookEnabled,
        boolean modelParseEnabled,
        long webhookMaxPayloadBytes,
        long importMaxContentBytes,
        long documentBinaryMaxBytes,
        boolean ocrConfigured,
        int ocrTimeoutSeconds,
        int ocrMaxOutputChars,
        int ocrMaxConcurrentProcesses,
        int ocrAvailablePermits,
        String ocrWorkerMode,
        boolean ocrRemoteWorkerConfigured,
        boolean ocrWorkerTokenConfigured,
        boolean ocrLocalCommandFallbackEnabled,
        boolean ocrLocalCommandExecutionAllowed,
        int batchActionLimit,
        boolean webhookIpAllowlistEnabled,
        boolean webhookTrustedProxyCidrsConfigured,
        boolean webhookRateLimitEnabled,
        int webhookRateLimitMaxRequests,
        long webhookRateLimitWindowSeconds,
        boolean binaryMimeValidationEnabled,
        int pdfMaxPages,
        long pdfMaxParseMillis,
        boolean malwareScanEnabled,
        int malwareScanTimeoutSeconds,
        int malwareScanMaxConcurrentProcesses,
        int malwareScanAvailablePermits,
        boolean webhookSecretCacheEnabled,
        long webhookSecretCacheTtlSeconds,
        long webhookSecretRotationOverlapSeconds,
        int webhookSecretCacheSize,
        DocumentSecretProviderHealthResponse externalSecretProvider
) {
}
