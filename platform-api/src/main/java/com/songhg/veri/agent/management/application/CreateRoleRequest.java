package com.songhg.veri.agent.management.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateRoleRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{2,63}$")
        String code,

        @NotBlank
        @Size(max = 64)
        String name,

        @NotBlank
        @Pattern(regexp = "^(PLATFORM|DEPARTMENT|PROJECT|APPLICATION|ENVIRONMENT)$")
        String scopeType,

        @Size(max = 512)
        String description,

        @NotEmpty
        @Size(max = 128)
        List<
                @NotBlank
                @Size(max = 128)
                @Pattern(regexp = "^[A-Za-z][A-Za-z0-9:_-]*$")
                String
        > permissionCodes
) {
}
