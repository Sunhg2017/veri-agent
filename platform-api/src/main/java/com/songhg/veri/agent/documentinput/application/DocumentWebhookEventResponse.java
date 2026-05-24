package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.documentinput.domain.WebhookEventStatus;
import com.songhg.veri.agent.documentinput.domain.WebhookSignatureStatus;
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
