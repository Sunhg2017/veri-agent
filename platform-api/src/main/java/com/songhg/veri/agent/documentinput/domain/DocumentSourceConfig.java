package com.songhg.veri.agent.documentinput.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentSourceConfig(
        UUID id,
        String sourceCode,
        String name,
        DocumentSourceType sourceType,
        DocumentSourceStatus status,
        String endpointUrl,
        String defaultProjectId,
        UUID mappingId,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
