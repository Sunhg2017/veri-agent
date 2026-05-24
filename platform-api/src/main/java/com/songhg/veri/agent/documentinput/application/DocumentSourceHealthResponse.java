package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.documentinput.domain.DocumentSourceStatus;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import com.songhg.veri.agent.documentinput.domain.WebhookEventStatus;
import com.songhg.veri.agent.documentinput.domain.WebhookSignatureStatus;
import java.time.Instant;
import java.util.UUID;

public record DocumentSourceHealthResponse(
        UUID sourceId,
        String sourceCode,
        DocumentSourceType sourceType,
        DocumentSourceStatus sourceStatus,
        boolean dataFlowSupported,
        boolean ready,
        String message,
        String webhookPath,
        String signatureAlgorithm,
        boolean secretRefConfigured,
        String eventVersion,
        String mappingVersion,
        Instant checkedAt,
        Instant lastEventAt,
        WebhookEventStatus lastEventStatus,
        WebhookSignatureStatus lastSignatureStatus,
        String lastErrorMessage
) {
}
