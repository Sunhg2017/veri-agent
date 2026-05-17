package com.songhg.veri.agent.management.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record RoleBindingRequest(
        @NotBlank
        @JsonProperty("role_code")
        String roleCode
) {
}
