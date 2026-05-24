package com.songhg.veri.agent.documentinput.application.view;

import com.songhg.veri.agent.documentinput.domain.DocumentCandidateStatus;
import java.time.Instant;
import java.util.UUID;

public record DocumentCandidateResponse(
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
}
