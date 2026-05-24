package com.songhg.veri.agent.management.application;


public record RoleView(
        String code,
        String name,
        String scopeType,
        String status,
        String description
) {
}
