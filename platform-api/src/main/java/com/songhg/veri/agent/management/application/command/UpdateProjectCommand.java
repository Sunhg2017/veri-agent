package com.songhg.veri.agent.management.application.command;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProjectCommand(
        @Size(max = 64)
        String name,

        @Pattern(regexp = "^(|PUBLIC|INTERNAL|CONFIDENTIAL|STRICT)$")
        String sensitivityLevel,

        Boolean allowPublicModel
) {
}
