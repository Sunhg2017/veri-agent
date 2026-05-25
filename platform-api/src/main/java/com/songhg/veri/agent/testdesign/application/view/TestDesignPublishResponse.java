package com.songhg.veri.agent.testdesign.application.view;

import java.util.List;
import java.util.UUID;

public record TestDesignPublishResponse(
        UUID taskId,
        String projectId,
        boolean dryRun,
        int total,
        int created,
        int skipped,
        int failed,
        List<UUID> createdCaseIds,
        List<TestDesignPublishRecordResponse> records
) {
}
