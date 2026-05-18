package com.songhg.veri.agent.integration.infrastructure;

public record PlatformContextRow(
        String resourceId,
        String status,
        String sensitivityLevel,
        boolean allowPublicModel
) {
}
