package com.songhg.veri.agent.management.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserView(
        String username,
        @JsonProperty("display_name")
        String displayName,
        String email,
        String role,
        String department,
        String status,
        @JsonProperty("last_seen")
        String lastSeen
) {
}
