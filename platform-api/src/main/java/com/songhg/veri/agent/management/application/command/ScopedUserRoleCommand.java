package com.songhg.veri.agent.management.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ScopedUserRoleCommand(
        @Schema(description = "用户名")
        @NotBlank
        @Size(max = 64)
        String username,
        @Schema(description = "角色编码")
        @NotBlank
        @Pattern(regexp = "^(AppOwner|Tester|Developer|Auditor)$")
        String roleCode
) {
}
