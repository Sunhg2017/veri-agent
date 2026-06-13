package com.songhg.veri.agent.execution.application.query;

import java.util.UUID;

public record ExecutionTriggerEventQuery(
        UUID triggerId,
        String status,
        int limit,
        int offset
) {
}
