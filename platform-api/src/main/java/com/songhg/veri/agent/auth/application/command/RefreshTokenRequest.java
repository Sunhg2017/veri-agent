package com.songhg.veri.agent.auth.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank
        @Size(max = 512)
        String refreshToken
) {
}
