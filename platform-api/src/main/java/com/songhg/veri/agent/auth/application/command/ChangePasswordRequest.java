package com.songhg.veri.agent.auth.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @Schema(description = "当前密码")
        @NotBlank
        @Size(min = 1, max = 128)
        String oldPassword,
        @Schema(description = "新密码")
        @NotBlank
        @Size(min = 10, max = 128)
        String newPassword
) {
    @Override
    public String toString() {
        return "ChangePasswordRequest[oldPassword=<masked>, newPassword=<masked>]";
    }
}
