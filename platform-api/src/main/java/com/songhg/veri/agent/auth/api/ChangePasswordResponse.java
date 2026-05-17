package com.songhg.veri.agent.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record ChangePasswordResponse(
        @JsonProperty("password_changed") boolean passwordChanged,
        @JsonProperty("session_invalidated") boolean sessionInvalidated,
        @JsonProperty("user_id") UUID userId
) {
}
