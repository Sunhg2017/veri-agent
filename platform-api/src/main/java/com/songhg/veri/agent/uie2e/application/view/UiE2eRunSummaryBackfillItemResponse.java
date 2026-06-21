package com.songhg.veri.agent.uie2e.application.view;

import java.util.UUID;

public record UiE2eRunSummaryBackfillItemResponse(
        UUID runId,
        UUID sceneId,
        String status,
        boolean updated,
        int stepResultCount,
        int artifactCount,
        String errorCode,
        String errorMessage
) {
}
