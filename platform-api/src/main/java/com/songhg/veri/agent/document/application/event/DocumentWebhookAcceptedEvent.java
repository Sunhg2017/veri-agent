package com.songhg.veri.agent.document.application.event;

import java.util.UUID;

public record DocumentWebhookAcceptedEvent(UUID webhookEventId) {

    public static final String EVENT_TYPE = "document-input.webhook.accepted";
}
