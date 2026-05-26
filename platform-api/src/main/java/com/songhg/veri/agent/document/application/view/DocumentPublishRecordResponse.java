package com.songhg.veri.agent.document.application.view;

import com.songhg.veri.agent.document.domain.DocumentCandidateStatus;
import java.util.UUID;

public record DocumentPublishRecordResponse(
        UUID candidateId,
        String title,
        DocumentCandidateStatus candidateStatus,
        String action,
        String result,
        String projectId,
        String externalRequirementId,
        String sourceRef,
        String sourceFragment,
        UUID assetRequirementId,
        UUID existingRequirementId,
        String diffSummary,
        String errorMessage,
        long version
) {
}
