package com.songhg.veri.agent.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import java.util.Set;
import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        @JsonProperty("user_id")
        UUID userId,
        String username,
        @JsonProperty("display_name")
        String displayName,
        String email,
        @JsonProperty("must_change_password")
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
