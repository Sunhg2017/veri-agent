package com.songhg.veri.agent.management.application.command;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 64)
        String displayName,

        @Email
        @Size(max = 128)
        String email
) {
}
