package com.songhg.veri.agent.documentinput.api.response;

import com.songhg.veri.agent.documentinput.domain.DocumentCandidateStatus;
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
        String errorMessage,
        long version
) {
}
