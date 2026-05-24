package com.songhg.veri.agent.auth.application.view;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        UUID sessionId,
        String tokenType,
        Instant expiresAt,
        UUID userId,
        String username,
        String displayName,
        String email,
        boolean mustChangePassword,
        List<String> roles
) {
}
