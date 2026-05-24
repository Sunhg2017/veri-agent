package com.songhg.veri.agent.documentinput.api.response;

import com.songhg.veri.agent.documentinput.application.view.DocumentPublishRecordResponse;
import com.songhg.veri.agent.documentinput.domain.DocumentImportStatus;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


public record DocumentPublishResponse(
        UUID id,
        UUID importId,
        String projectId,
        UUID sourceId,
        String sourceCode,
        DocumentSourceType sourceType,
        String sourceRef,
        String sourceUrl,
        String title,
        DocumentImportStatus status,
        boolean dryRun,
        int totalParsed,
        int totalCreated,
        List<UUID> createdRequirementIds,
        long pendingCount,
        long confirmedCount,
        long publishedCount,
        long failedCount,
        int plannedCreateCount,
        int plannedUpdateCount,
        int linkedExistingCount,
        int conflictCount,
        int skippedCount,
        int publishFailedCount,
        List<DocumentPublishRecordResponse> records,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
