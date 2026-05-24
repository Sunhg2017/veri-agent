package com.songhg.veri.agent.management.application.view;


public record ScopedUserRoleView(
        String username,
        String displayName,
        String role,
        String scopeType,
        String status
) {
}
