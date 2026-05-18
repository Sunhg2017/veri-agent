package com.songhg.veri.agent.integration.application;

import java.time.Instant;
import java.util.List;

public record PlatformContext(
        String resourceType,
        String resourceId,
        String status,
        String sensitivityLevel,
        boolean allowPublicModel,
        List<String> include,
        Instant validatedAt
) {
}
