package com.songhg.veri.agent.management.application;


public record ScopedUserRoleView(
        String username,
        String displayName,
        String role,
        String scopeType,
        String status
) {
}
