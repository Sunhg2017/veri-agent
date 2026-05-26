package com.songhg.veri.agent.document.application.view;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record DocumentParseFeedbackSampleResponse(
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
        JsonNode beforeSnapshot,
        JsonNode afterSnapshot,
        String curationStatus,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
