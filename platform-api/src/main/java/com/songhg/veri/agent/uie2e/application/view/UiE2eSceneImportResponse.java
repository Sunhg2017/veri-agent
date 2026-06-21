package com.songhg.veri.agent.uie2e.application.view;

import java.util.List;
import java.util.Map;

public record UiE2eSceneImportResponse(
        String projectId,
        String applicationId,
        String environmentId,
        String code,
        String name,
        String status,
        String riskLevel,
        List<String> tags,
        Map<String, Object> sourceSummary,
        List<UiE2eSceneImportStepResponse> steps,
        List<String> warnings,
        Map<String, Object> importSummary
) {
}
