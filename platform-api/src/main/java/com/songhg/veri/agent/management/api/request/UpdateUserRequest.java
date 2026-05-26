package com.songhg.veri.agent.management.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Schema(description = "用户显示名")
        @Size(max = 64)
        String displayName,
        @Schema(description = "邮箱地址")
        @Email
        @Size(max = 128)
        String email
) {
}
