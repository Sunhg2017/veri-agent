package com.songhg.veri.agent.management.application.view;


public record ProjectMemberView(
        String username,
        String displayName,
        String role,
        String memberType,
        String status
) {
}
