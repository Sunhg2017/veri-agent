package com.songhg.veri.agent.documentinput.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentWebhookEvent(
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
        String rawPayload,
        String errorMessage,
        int retryCount,
        Instant receivedAt,
        Instant processedAt
) {
}
