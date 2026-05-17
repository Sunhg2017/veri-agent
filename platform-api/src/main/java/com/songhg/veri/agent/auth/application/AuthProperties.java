package com.songhg.veri.agent.auth.application;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "veri-agent.auth")
public record AuthProperties(
        String tokenSecret,
        @Min(1) long accessTokenTtlMinutes
) {
}
