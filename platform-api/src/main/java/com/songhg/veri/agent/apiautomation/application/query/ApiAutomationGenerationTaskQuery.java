package com.songhg.veri.agent.apiautomation.application.query;

import java.util.UUID;

public record ApiAutomationGenerationTaskQuery(
        String projectId,
        UUID specId,
        String status,
        int limit,
        int offset
) {
}
