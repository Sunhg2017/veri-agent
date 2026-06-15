package com.songhg.veri.agent.testdata.domain;

import java.time.Instant;
import java.util.UUID;

public record TestDataRecord(
        UUID id,
        UUID dataSetId,
        String projectId,
        String recordKey,
        String status,
        String recordDigest,
        String maskedSummaryJson,
        String externalRefDigest,
        String tagsJson,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
