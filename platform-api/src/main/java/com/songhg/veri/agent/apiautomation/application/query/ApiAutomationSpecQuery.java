package com.songhg.veri.agent.apiautomation.application.query;

public record ApiAutomationSpecQuery(
        String projectId,
        String status,
        String keyword,
        int limit,
        int offset
) {
}
