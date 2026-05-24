package com.songhg.veri.agent.management.api.response;


public record RoleResponse(
        String code,
        String name,
        String scopeType,
        String status,
        String description
) {
}
