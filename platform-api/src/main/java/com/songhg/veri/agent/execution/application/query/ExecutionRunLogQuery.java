package com.songhg.veri.agent.execution.application.query;

public record ExecutionRunLogQuery(
        String level,
        int limit,
        int offset
) {
}
