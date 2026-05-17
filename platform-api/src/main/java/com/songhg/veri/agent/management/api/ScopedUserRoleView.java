package com.songhg.veri.agent.management.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ScopedUserRoleView(
        String username,
        @JsonProperty("display_name")
        String displayName,
        String role,
        @JsonProperty("scope_type")
        String scopeType,
        String status
) {
}
