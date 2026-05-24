package com.songhg.veri.agent.management.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateApplicationCommand(
        @Size(max = 32)
        @Pattern(regexp = "^[A-Za-z0-9_-]*$")
        String code,

        @NotBlank
        @Size(max = 64)
        String name,

        @Size(max = 64)
        String project,

        @Pattern(regexp = "^(|Web|Backend|Frontend|Mobile|Service|API)$")
        String appType,

        @Size(max = 512)
        String defaultWebUrl,

        @Size(max = 512)
        String defaultApiBaseUrl,

        @Pattern(regexp = "^(|PUBLIC|INTERNAL|CONFIDENTIAL|STRICT)$")
        String sensitivityLevel,

        Boolean allowPublicModel
) {
}
