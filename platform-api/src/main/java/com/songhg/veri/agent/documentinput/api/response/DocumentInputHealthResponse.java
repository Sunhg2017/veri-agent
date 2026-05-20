package com.songhg.veri.agent.documentinput.api.response;

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
        int malwareScanAvailablePermits
) {
}
