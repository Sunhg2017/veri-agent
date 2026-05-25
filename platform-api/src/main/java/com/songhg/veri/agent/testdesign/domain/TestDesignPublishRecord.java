package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

public record TestDesignPublishRecord(
        UUID id,
        UUID taskId,
        UUID candidateId,
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
