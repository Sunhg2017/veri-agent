package com.songhg.veri.agent.integration.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record PlatformContextResponse(
        @JsonProperty("resource_type") String resourceType,
        @JsonProperty("resource_id") String resourceId,
        String status,
        @JsonProperty("sensitivity_level") String sensitivityLevel,
        @JsonProperty("allow_public_model") boolean allowPublicModel,
        List<String> include,
        @JsonProperty("validated_at") Instant validatedAt
) {
}
