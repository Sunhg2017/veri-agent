package com.songhg.veri.agent.execution.application.query;

import java.util.UUID;

public record ExecutionTriggerQuery(
        UUID planId,
        String triggerType,
        String status,
        int limit,
        int offset
) {
}
