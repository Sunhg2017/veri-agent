package com.songhg.veri.agent.document.application.view;

import com.songhg.veri.agent.document.domain.WebhookEventStatus;
import com.songhg.veri.agent.document.domain.WebhookSignatureStatus;
import java.time.Instant;
import java.util.UUID;

public record DocumentWebhookEventResponse(
        UUID id,
        UUID sourceId,
        UUID importId,
        String sourceCode,
        String eventId,
        String idempotencyKey,
        String eventType,
        String eventVersion,
        WebhookSignatureStatus signatureStatus,
        WebhookEventStatus status,
        String payloadDigest,
        String errorMessage,
        int retryCount,
        String replayBy,
        Instant replayAt,
        String replayTraceId,
        Instant receivedAt,
        Instant processedAt
) {
}
