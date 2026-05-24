package com.songhg.veri.agent.management.application;


public record ProjectMemberView(
        String username,
        String displayName,
        String role,
        String memberType,
        String status
) {
}
