package com.songhg.veri.agent.management.application;

import jakarta.validation.constraints.NotBlank;

public record RoleBindingRequest(
        @NotBlank
        String roleCode
) {
}
