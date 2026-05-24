package com.songhg.veri.agent.management.api.response;


public record ScopedUserRoleResponse(
        String username,
        String displayName,
        String role,
        String scopeType,
        String status
) {
}
