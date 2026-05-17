package com.songhg.veri.agent.bootstrap.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "veri-agent.bootstrap")
public record BootstrapProperties(
        String token
) {
}

