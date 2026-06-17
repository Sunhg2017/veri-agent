package com.songhg.veri.agent.reporting.application.event;

import java.util.UUID;

public record ReportWebhookDeliveryRequestedEvent(
        UUID reportId,
        String terminalStatus
) {
    public static final String EVENT_TYPE = "report.webhook.delivery.requested";
}
