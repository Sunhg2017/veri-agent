package com.songhg.veri.agent.auth.api.response;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CurrentUserResponse(
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
        List<String> roles,
        @Schema(description = "用户拥有的权限编码列表")
        Set<String> permissions
) {
    public static CurrentUserResponse from(AuthUserPrincipal principal, Set<String> permissions) {
        return new CurrentUserResponse(
                principal.userId(),
                principal.username(),
                principal.displayName(),
                principal.email(),
                principal.mustChangePassword(),
                principal.roles(),
                permissions
        );
    }
}
