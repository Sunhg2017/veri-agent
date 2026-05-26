package com.songhg.veri.agent.auth.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(description = "用户名。")
        @NotBlank
        @Size(min = 3, max = 64)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "账号只能包含字母、数字、下划线和中划线")
        String username,
        @Schema(description = "登录密码。")
        @NotBlank
        @Size(min = 1, max = 128)
        String password
) {
    @Override
    public String toString() {
        return "LoginRequest[username=%s, password=<masked>]".formatted(username);
    }
}
