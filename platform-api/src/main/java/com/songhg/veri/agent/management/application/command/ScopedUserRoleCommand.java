package com.songhg.veri.agent.management.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ScopedUserRoleCommand(
        @NotBlank
        @Size(max = 64)
        String username,

        @NotBlank
        @Pattern(regexp = "^(AppOwner|Tester|Developer|Auditor)$")
        String roleCode
) {
}
