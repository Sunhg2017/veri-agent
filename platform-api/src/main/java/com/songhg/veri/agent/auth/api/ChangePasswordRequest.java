package com.songhg.veri.agent.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank
        @Size(min = 1, max = 128)
        @JsonProperty("old_password")
        String oldPassword,

        @NotBlank
        @Size(min = 10, max = 128)
        @JsonProperty("new_password")
        String newPassword
) {
}
