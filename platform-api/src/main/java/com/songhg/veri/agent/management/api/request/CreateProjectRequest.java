package com.songhg.veri.agent.management.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @Size(max = 32)
        @Pattern(regexp = "^[A-Za-z0-9_-]*$")
        String code,

        @NotBlank
        @Size(max = 64)
        String name,

        @Pattern(regexp = "^(|PUBLIC|INTERNAL|CONFIDENTIAL|STRICT)$")
        String sensitivityLevel,

        Boolean allowPublicModel
) {
}
