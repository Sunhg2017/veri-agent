package com.songhg.veri.agent.management.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoleView(
        String code,
        String name,
        @JsonProperty("scope_type") String scopeType,
        String status,
        String description
) {
}
