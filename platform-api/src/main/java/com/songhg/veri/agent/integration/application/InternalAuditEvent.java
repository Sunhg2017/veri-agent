package com.songhg.veri.agent.integration.application;

import java.util.Map;

public record InternalAuditEvent(
        String traceId,
        String actorService,
        String action,
        String resourceType,
        String resourceId,
        String scopeType,
        String scopeId,
        String result,
        String reason,
        Map<String, Object> afterJson
) {
}
