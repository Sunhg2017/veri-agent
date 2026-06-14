package com.songhg.veri.agent.testdata.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TestDataReportEvidenceResponse(
        String projectId,
        String reportRef,
        List<DataSetEvidence> dataSets,
        List<AccountLeaseEvidence> accountLeases,
        List<CleanupTaskEvidence> cleanupTasks,
        Map<String, Object> redactionPolicy
) {

    public record DataSetEvidence(
            UUID dataSetRef,
            String applicationId,
            String environmentId,
            String code,
            String status,
            String sensitivityLevel,
            int schemaFieldCount,
            long recordCount,
            String cleanupPolicyDigest,
            String sourceRefDigest
    ) {
    }

    public record AccountLeaseEvidence(
            UUID accountLeaseRef,
            String status,
            String holderType,
            String holderRef,
            Instant expiresAt,
            Instant releasedAt,
            TestDataCrossWpAccountSummary account
    ) {
    }

    public record CleanupTaskEvidence(
            UUID cleanupTaskRef,
            UUID dataSetRef,
            String taskType,
            String status,
            String targetRefDigest,
            int attempt,
            String resultSummaryDigest,
            List<String> resultSummaryKeys,
            String errorCode,
            String errorSummaryDigest,
            String traceId,
            Instant startedAt,
            Instant finishedAt
    ) {
    }
}
