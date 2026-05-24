package com.songhg.veri.agent.management.api.response;


public record ProjectMemberResponse(
        String username,
        String displayName,
        String role,
        String memberType,
        String status
) {
}
