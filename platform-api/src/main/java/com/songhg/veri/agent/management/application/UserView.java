package com.songhg.veri.agent.management.application;


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
