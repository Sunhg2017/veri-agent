package com.songhg.veri.agent.document.application.view;

import com.songhg.veri.agent.document.domain.DocumentImportStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentImportResponse(
        UUID id,
        String projectId,
        UUID sourceId,
        String sourceCode,
        DocumentSourceType sourceType,
        String sourceRef,
        String sourceUrl,
        String title,
        DocumentImportStatus status,
        int totalParsed,
        int totalCreated,
        List<UUID> createdRequirementIds,
        List<ParsedRequirementResponse> requirements,
        long pendingCount,
        long confirmedCount,
        long publishedCount,
        long failedCount,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
