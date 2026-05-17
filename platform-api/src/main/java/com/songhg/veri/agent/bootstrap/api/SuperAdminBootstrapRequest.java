package com.songhg.veri.agent.bootstrap.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SuperAdminBootstrapRequest(
        @JsonProperty("bootstrap_token")
        @NotBlank
        @Size(max = 256)
        String bootstrapToken,

        @NotBlank
        @Size(min = 3, max = 64)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "账号只能包含字母、数字、下划线和中划线")
        String username,

        @NotBlank
        @Size(min = 10, max = 128)
        String password,

        @JsonProperty("display_name")
        @NotBlank
        @Size(max = 64)
        String displayName,

        @Email
        @Size(max = 128)
        String email
) {
}

