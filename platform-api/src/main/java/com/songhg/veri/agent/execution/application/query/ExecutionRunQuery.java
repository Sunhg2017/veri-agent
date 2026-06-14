package com.songhg.veri.agent.execution.application.query;

import java.util.UUID;

public record ExecutionRunQuery(
        String projectId,
        UUID planId,
        String status,
        int limit,
        int offset
) {
}
