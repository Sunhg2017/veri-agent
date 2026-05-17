package com.songhg.veri.agent.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record LogoutResponse(
        boolean revoked,
        @JsonProperty("session_id")
        UUID sessionId
) {
}
