package com.songhg.veri.agent.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentFieldMapping(
        UUID id,
        String mappingCode,
        String name,
        String itemPath,
        String titlePath,
        String descriptionPath,
        String priorityPath,
        String acceptanceCriteriaPath,
        String tagsPath,
        Instant createdAt,
        Instant updatedAt
) {
}
