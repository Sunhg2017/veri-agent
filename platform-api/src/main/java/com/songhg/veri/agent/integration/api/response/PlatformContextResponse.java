package com.songhg.veri.agent.integration.api.response;

import java.time.Instant;
import java.util.List;

public record PlatformContextResponse(
        String resourceType,
        String resourceId,
        String status,
        String sensitivityLevel,
        boolean allowPublicModel,
        List<String> include,
        Instant validatedAt
) {
}
