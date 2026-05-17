package com.songhg.veri.agent.integration.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "veri-agent.integration")
public record PlatformIntegrationProperties(
        String serviceToken
) {
}
