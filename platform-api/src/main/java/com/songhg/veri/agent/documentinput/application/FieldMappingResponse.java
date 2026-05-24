package com.songhg.veri.agent.documentinput.application;

import java.time.Instant;
import java.util.UUID;

public record FieldMappingResponse(
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
