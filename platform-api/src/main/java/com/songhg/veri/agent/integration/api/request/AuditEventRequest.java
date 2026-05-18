package com.songhg.veri.agent.integration.api.request;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record AuditEventRequest(
        @NotBlank String action,
        @NotBlank String resourceType,
        @NotBlank String resourceId,
        String scopeType,
        String scopeId,
        @NotBlank String result,
        String reason,
        Map<String, Object> afterJson
) {
}
