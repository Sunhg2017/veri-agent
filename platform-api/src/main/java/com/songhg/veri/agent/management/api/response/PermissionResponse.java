package com.songhg.veri.agent.management.api.response;

public record PermissionResponse(
        String code,
        String resourceType,
        String action,
        String scopeMask,
        String description,
        String status
) {
}
