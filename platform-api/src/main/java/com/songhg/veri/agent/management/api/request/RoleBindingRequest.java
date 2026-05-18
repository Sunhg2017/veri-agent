package com.songhg.veri.agent.management.api.request;

import jakarta.validation.constraints.NotBlank;

public record RoleBindingRequest(
        @NotBlank
        String roleCode
) {
}
