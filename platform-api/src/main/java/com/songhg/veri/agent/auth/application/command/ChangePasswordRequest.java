package com.songhg.veri.agent.auth.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank
        @Size(min = 1, max = 128)
        String oldPassword,

        @NotBlank
        @Size(min = 10, max = 128)
        String newPassword
) {
    @Override
    public String toString() {
        return "ChangePasswordRequest[oldPassword=<masked>, newPassword=<masked>]";
    }
}
