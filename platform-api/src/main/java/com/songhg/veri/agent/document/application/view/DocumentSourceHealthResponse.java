package com.songhg.veri.agent.document.application.view;

import com.songhg.veri.agent.document.domain.DocumentSourceStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import com.songhg.veri.agent.document.domain.WebhookEventStatus;
import com.songhg.veri.agent.document.domain.WebhookSignatureStatus;
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
