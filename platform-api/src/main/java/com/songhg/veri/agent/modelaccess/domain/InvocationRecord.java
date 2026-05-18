package com.songhg.veri.agent.modelaccess.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvocationRecord(
        UUID id,
        String projectId,
        String applicationId,
        String environmentId,
        String sensitivityLevel,
        String promptKey,
        Integer promptVersion,
        UUID providerId,
        String providerName,
        String modelName,
        InvocationStatus status,
        boolean fallbackUsed,
        String promptDigest,
        String requestPreview,
        String responsePreview,
        int inputTokens,
        int outputTokens,
        BigDecimal totalCost,
        String errorCode,
        String errorMessage,
        long latencyMs,
        String actorService,
        String delegatedUserId,
        Instant createdAt
) {
}
