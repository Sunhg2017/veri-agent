package com.songhg.veri.agent.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentParseFeedbackSample(
        UUID id,
        UUID candidateId,
        UUID importId,
        String projectId,
        String sourceType,
        String inputDigest,
        String sourceRefDigest,
        String sourceFragmentDigest,
        String parseSource,
        UUID modelInvocationId,
        String modelProviderName,
        String modelName,
        String correctionType,
        String changedFields,
        String beforeSnapshotJson,
        String afterSnapshotJson,
        String curationStatus,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
