package com.songhg.veri.agent.testdata.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TestDataRecordResponse(
        UUID id,
        UUID dataSetId,
        String projectId,
        String recordKey,
        String status,
        String recordDigest,
        Map<String, Object> maskedSummary,
        String externalRefDigest,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
) {
}
