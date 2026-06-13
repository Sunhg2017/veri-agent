package com.songhg.veri.agent.execution.application.query;

public record ExecutionPlanQuery(
        String projectId,
        String status,
        String keyword,
        int limit,
        int offset
) {
}
