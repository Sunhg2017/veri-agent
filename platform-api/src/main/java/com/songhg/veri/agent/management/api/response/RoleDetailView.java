package com.songhg.veri.agent.management.api.response;

import java.util.List;

public record RoleDetailView(
        String code,
        String name,
        String scopeType,
        String status,
        String description,
        boolean system,
        boolean builtin,
        long version,
        List<String> permissionCodes
) {
}
