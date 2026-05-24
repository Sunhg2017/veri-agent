package com.songhg.veri.agent.management.application.command;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateRoleCommand(
        @Size(max = 64)
        String name,

        @Pattern(regexp = "^(|PLATFORM|DEPARTMENT|PROJECT|APPLICATION|ENVIRONMENT)$")
        String scopeType,

        @Size(max = 512)
        String description,

        @Size(max = 128)
        List<
                @Size(max = 128)
                @Pattern(regexp = "^[A-Za-z][A-Za-z0-9:_-]*$")
                String
        > permissionCodes
) {
}
