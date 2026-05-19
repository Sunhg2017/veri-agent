package com.songhg.veri.agent.documentinput.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
        Map<String, String> webhookSecrets
) {
}
