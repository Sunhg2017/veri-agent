package com.songhg.veri.agent.bootstrap.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SuperAdminBootstrapResponse(
        @JsonProperty("user_id")
        String userId,
        String role,
        @JsonProperty("must_change_password")
        boolean mustChangePassword
) {
}

