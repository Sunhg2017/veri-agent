package com.songhg.veri.agent.management.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProjectMemberRequest(
        @NotBlank
        @Size(max = 64)
        String username,

        @NotBlank
        @Pattern(regexp = "^(ProjectOwner|Tester|Developer|Auditor)$")
        String roleCode
) {
}
