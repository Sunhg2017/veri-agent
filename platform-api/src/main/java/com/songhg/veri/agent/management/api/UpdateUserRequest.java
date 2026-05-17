package com.songhg.veri.agent.management.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @JsonProperty("display_name")
        @Size(max = 64)
        String displayName,

        @Email
        @Size(max = 128)
        String email
) {
}
