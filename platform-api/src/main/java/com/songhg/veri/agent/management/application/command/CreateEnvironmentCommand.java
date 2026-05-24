package com.songhg.veri.agent.management.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEnvironmentCommand(
        @Size(max = 32)
        @Pattern(regexp = "^[A-Za-z0-9_-]*$")
        String code,

        @NotBlank
        @Size(max = 64)
        String name,

        @Size(max = 64)
        String project,

        @Size(max = 64)
        String application,

        @Pattern(regexp = "^(|PROJECT|APPLICATION)$")
        String scopeType,

        @Pattern(regexp = "^(|DEV|TEST|STAGING|PREPROD|PROD)$")
        String envType,

        @Size(max = 512)
        String webUrl,

        @Size(max = 512)
        String apiBaseUrl
) {
}
