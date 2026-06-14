package com.songhg.veri.agent.testdata.application.query;

public record TestDataSetQuery(
        String projectId,
        String applicationId,
        String environmentId,
        String status,
        String keyword,
        long offset,
        int limit
) {
}
