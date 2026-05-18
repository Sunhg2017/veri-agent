package com.songhg.veri.agent.management.api.response;


public record UserView(
        String username,
        String displayName,
        String email,
        String role,
        String department,
        String status,
        String lastSeen
) {
}
