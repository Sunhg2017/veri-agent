package com.songhg.veri.agent.asset.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "veri-agent.asset")
public record AssetProperties(
        String serviceToken
) {
}
