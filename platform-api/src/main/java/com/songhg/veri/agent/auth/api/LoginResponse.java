package com.songhg.veri.agent.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LoginResponse(
        @JsonProperty("access_token")
        String accessToken,
        @JsonProperty("refresh_token")
        String refreshToken,
        @JsonProperty("session_id")
        UUID sessionId,
        @JsonProperty("token_type")
        String tokenType,
        @JsonProperty("expires_at")
        Instant expiresAt,
        @JsonProperty("user_id")
        UUID userId,
        String username,
        @JsonProperty("display_name")
        String displayName,
        String email,
        @JsonProperty("must_change_password")
        boolean mustChangePassword,
        List<String> roles
) {
}
