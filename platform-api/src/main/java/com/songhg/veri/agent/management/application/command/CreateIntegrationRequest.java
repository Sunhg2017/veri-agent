package com.songhg.veri.agent.management.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateIntegrationRequest(
        @Size(max = 32)
        @Pattern(regexp = "^[A-Za-z0-9_-]*$")
        String code,

        @NotBlank
        @Size(max = 64)
        String name,

        @Size(max = 64)
        String category,

        @Size(max = 64)
        String scope
) {
}
