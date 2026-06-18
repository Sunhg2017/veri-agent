package com.songhg.veri.agent.uie2e.application.query;

public record UiE2eSceneQuery(
        String projectId,
        String applicationId,
        String environmentId,
        String status,
        String riskLevel,
        String tag,
        String keyword,
        long offset,
        int limit
) {
}
