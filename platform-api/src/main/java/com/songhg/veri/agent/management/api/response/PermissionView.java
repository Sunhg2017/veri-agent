package com.songhg.veri.agent.management.api.response;

public record PermissionView(
        String code,
        String resourceType,
        String action,
        String scopeMask,
        String description,
        String status
) {
}
