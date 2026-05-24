package com.songhg.veri.agent.management.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSettingCommand(
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$")
        String key,

        @Size(max = 64)
        String name,

        @NotBlank
        @Size(max = 256)
        String value,

        @Pattern(regexp = "^(|SYSTEM|PROJECT|APPLICATION|ENVIRONMENT)$")
        String scopeType
) {
}
