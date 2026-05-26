package com.songhg.veri.agent.document.api.response;

import com.songhg.veri.agent.document.domain.DocumentSourceStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import java.time.Instant;
import java.util.UUID;

public record DocumentSourceResponse(
        UUID id,
        String sourceCode,
        String name,
        DocumentSourceType sourceType,
        DocumentSourceStatus status,
        String endpointUrl,
        String defaultProjectId,
        UUID mappingId,
        String secretRef,
        String eventVersion,
        String mappingVersion,
        String description,
        boolean dataFlowSupported,
        Instant createdAt,
        Instant updatedAt
) {
}
