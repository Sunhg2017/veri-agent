package com.songhg.veri.agent.management.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank
        @Size(min = 10, max = 128)
        String newPassword
) {
}
