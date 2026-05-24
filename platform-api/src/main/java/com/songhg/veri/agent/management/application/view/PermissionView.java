package com.songhg.veri.agent.management.application.view;

public record PermissionView(
        String code,
        String resourceType,
        String action,
        String scopeMask,
        String description,
        String status
) {
}
