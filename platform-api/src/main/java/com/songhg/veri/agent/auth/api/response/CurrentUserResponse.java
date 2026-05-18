package com.songhg.veri.agent.auth.api.response;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import java.util.Set;
import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID userId,
        String username,
        String displayName,
        String email,
        boolean mustChangePassword,
        List<String> roles,
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
