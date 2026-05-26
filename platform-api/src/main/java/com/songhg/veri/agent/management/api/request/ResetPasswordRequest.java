package com.songhg.veri.agent.management.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @Schema(description = "新密码。")
        @NotBlank
        @Size(min = 10, max = 128)
        String newPassword
) {
}
