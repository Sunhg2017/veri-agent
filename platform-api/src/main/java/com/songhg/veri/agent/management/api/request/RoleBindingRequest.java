package com.songhg.veri.agent.management.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RoleBindingRequest(
        @Schema(description = "角色编码")
        @NotBlank
        String roleCode
) {
}
