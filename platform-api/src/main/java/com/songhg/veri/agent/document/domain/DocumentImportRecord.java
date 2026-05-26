package com.songhg.veri.agent.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentImportRecord(
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
        String createdRequirementIds,
        String errorMessage,
        String rawDigest,
        Instant createdAt,
        Instant updatedAt
) {
}
