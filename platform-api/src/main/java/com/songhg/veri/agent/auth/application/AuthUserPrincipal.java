package com.songhg.veri.agent.auth.application;

import java.util.List;
import java.util.UUID;

public record AuthUserPrincipal(
        UUID userId,
        UUID sessionId,
        String username,
        String displayName,
        String email,
        boolean mustChangePassword,
        long authVersion,
        List<String> roles
) {
}
