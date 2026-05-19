package com.songhg.veri.agent.documentinput.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "veri-agent.document-input")
public record DocumentInputProperties(
        String serviceToken,
        String webhookSecret,
        long webhookClockSkewSeconds,
        boolean inputEnabled,
        boolean webhookEnabled,
        boolean modelParseEnabled,
        long webhookMaxPayloadBytes,
        int batchActionLimit,
        int webhookMaxReplayAttempts
) {
}
