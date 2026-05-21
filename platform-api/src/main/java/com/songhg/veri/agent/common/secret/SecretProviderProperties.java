package com.songhg.veri.agent.common.secret;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "veri-agent.secret")
public record SecretProviderProperties(
        String localMasterKey,
        String localMasterKeyVersion,
        String externalResolveUrl,
        String externalAuthToken,
        int externalTimeoutSeconds,
        int externalMaxRetries,
        String externalHealthUrl
) {
}
