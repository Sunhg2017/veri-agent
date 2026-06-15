package com.songhg.veri.agent.testdata.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TestDataSetExportResponse(
        String schemaVersion,
        Instant exportedAt,
        DataSetSnapshot dataSet,
        long recordCount,
        int schemaFieldCount,
        int sensitiveFieldCount,
        List<RecordSnapshot> records,
        Map<String, Object> redactionPolicy
) {

    public record DataSetSnapshot(
            UUID id,
            String projectId,
            String applicationId,
            String environmentId,
            String code,
            String name,
            String status,
            String sensitivityLevel,
            String sourceType,
            String sourceRefDigest,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record RecordSnapshot(
            String recordKey,
            String recordDigest,
            String externalRefDigest,
            List<String> tags,
            List<String> maskedSummaryKeys,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
