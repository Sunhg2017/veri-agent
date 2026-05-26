package com.songhg.veri.agent.auth.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LoginResponse(
        @Schema(description = "访问令牌")
        String accessToken,
        @Schema(description = "刷新令牌")
        String refreshToken,
        @Schema(description = "会话 ID")
        UUID sessionId,
        @Schema(description = "令牌类型")
        String tokenType,
        @Schema(description = "过期时间")
        Instant expiresAt,
        @Schema(description = "用户 ID")
        UUID userId,
        @Schema(description = "用户名")
        String username,
        @Schema(description = "用户显示名")
        String displayName,
        @Schema(description = "邮箱地址")
        String email,
        @Schema(description = "是否必须修改密码后才能继续使用系统")
        boolean mustChangePassword,
        @Schema(description = "用户拥有的角色列表")
        List<String> roles
) {
}
