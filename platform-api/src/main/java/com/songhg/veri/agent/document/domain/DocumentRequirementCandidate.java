package com.songhg.veri.agent.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentRequirementCandidate(
        UUID id,
        UUID importId,
        String projectId,
        String title,
        String description,
        String priority,
        String acceptanceCriteria,
        String tags,
        DocumentCandidateStatus status,
        String sourceRef,
        String sourceFragment,
        String externalRequirementId,
        double confidence,
        String parseSource,
        UUID modelInvocationId,
        String modelProviderName,
        String modelName,
        UUID assetRequirementId,
        String errorMessage,
        String ignoredReason,
        String confirmedBy,
        Instant confirmedAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {

    public DocumentRequirementCandidate(
            UUID id,
            UUID importId,
            String projectId,
            String title,
            String description,
            String priority,
            String acceptanceCriteria,
            String tags,
            DocumentCandidateStatus status,
            String sourceRef,
            String sourceFragment,
            String externalRequirementId,
            double confidence,
            UUID assetRequirementId,
            String errorMessage,
            String ignoredReason,
            String confirmedBy,
            Instant confirmedAt,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id,
                importId,
                projectId,
                title,
                description,
                priority,
                acceptanceCriteria,
                tags,
                status,
                sourceRef,
                sourceFragment,
                externalRequirementId,
                confidence,
                "RULE",
                null,
                null,
                null,
                assetRequirementId,
                errorMessage,
                ignoredReason,
                confirmedBy,
                confirmedAt,
                version,
                createdAt,
                updatedAt
        );
    }
}
