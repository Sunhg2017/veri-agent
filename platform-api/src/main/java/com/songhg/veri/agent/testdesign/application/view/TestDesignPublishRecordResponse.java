package com.songhg.veri.agent.testdesign.application.view;

import java.time.Instant;
import java.util.UUID;

public record TestDesignPublishRecordResponse(
        UUID id,
        UUID taskId,
        UUID candidateId,
        String title,
        String projectId,
        UUID requirementId,
        UUID assetCaseId,
        boolean dryRun,
        String action,
        String result,
        String errorMessage,
        String publishedBy,
        Instant createdAt
) {
}
