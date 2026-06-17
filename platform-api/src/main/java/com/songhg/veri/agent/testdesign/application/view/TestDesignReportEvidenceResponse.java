package com.songhg.veri.agent.testdesign.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TestDesignReportEvidenceResponse(
        String projectId,
        String reportRef,
        List<TaskEvidence> tasks,
        List<CandidateEvidence> candidates,
        Map<String, Object> redactionPolicy
) {

    public record TaskEvidence(
            UUID taskRef,
            String status,
            int requirementRefCount,
            int coverageTypeCount,
            int totalRequirements,
            int generatedCount,
            int confirmedCount,
            int publishedCount,
            boolean modelInvocationPresent,
            String requestDigest,
            String inputDigest,
            int contextSummaryKeyCount,
            long candidateCount,
            Map<String, Long> candidateStatusCounts,
            long reportManifestCount,
            long aggregateReportManifestCount,
            String latestReportManifestStatus,
            String latestReportManifestContentDigest,
            String latestReportManifestSchemaVersion,
            Instant updatedAt
    ) {
    }

    public record CandidateEvidence(
            UUID candidateRef,
            UUID taskRef,
            UUID requirementRef,
            UUID apiRef,
            UUID assetCaseRef,
            String status,
            String coverageType,
            String priority,
            double confidence,
            boolean modelInvocationPresent,
            boolean confirmed,
            long version,
            Instant updatedAt
    ) {
    }
}
