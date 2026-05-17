package com.songhg.veri.agent.integration.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record AuditEventRequest(
        @NotBlank String action,
        @NotBlank @JsonProperty("resource_type") String resourceType,
        @NotBlank @JsonProperty("resource_id") String resourceId,
        @JsonProperty("scope_type") String scopeType,
        @JsonProperty("scope_id") String scopeId,
        @NotBlank String result,
        String reason,
        @JsonProperty("after_json") Map<String, Object> afterJson
) {
}
