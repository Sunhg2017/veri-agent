package com.songhg.veri.agent.testdata.application.query;

import java.util.UUID;

public record TestDataTaskQuery(
        String projectId,
        UUID dataSetId,
        String taskType,
        String status,
        long offset,
        int limit
) {
}
