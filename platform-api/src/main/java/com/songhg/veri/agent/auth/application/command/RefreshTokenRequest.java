package com.songhg.veri.agent.auth.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @Schema(description = "刷新令牌")
        @NotBlank
        @Size(max = 512)
        String refreshToken
) {
}
