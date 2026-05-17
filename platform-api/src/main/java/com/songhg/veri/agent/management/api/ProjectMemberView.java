package com.songhg.veri.agent.management.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProjectMemberView(
        String username,
        @JsonProperty("display_name")
        String displayName,
        String role,
        @JsonProperty("member_type")
        String memberType,
        String status
) {
}
