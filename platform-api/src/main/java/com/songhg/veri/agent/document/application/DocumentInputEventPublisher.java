package com.songhg.veri.agent.document.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.event.PlatformEventEnvelope;
import com.songhg.veri.agent.common.event.PlatformEventProperties;
import com.songhg.veri.agent.common.event.PlatformEventPublisher;
import com.songhg.veri.agent.document.application.event.DocumentImportRequestedEvent;
import com.songhg.veri.agent.document.application.event.DocumentPublishRequestedEvent;
import com.songhg.veri.agent.document.application.event.DocumentWebhookAcceptedEvent;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class DocumentInputEventPublisher {

    private final PlatformEventPublisher eventPublisher;
    private final PlatformEventProperties eventProperties;
    private final ObjectMapper objectMapper;

    DocumentInputEventPublisher(
            @Lazy PlatformEventPublisher eventPublisher,
            PlatformEventProperties eventProperties,
            ObjectMapper objectMapper
    ) {
        this.eventPublisher = eventPublisher;
        this.eventProperties = eventProperties;
        this.objectMapper = objectMapper;
    }

    public void publishImportRequested(UUID importId) {
        PlatformEventEnvelope event = PlatformEventEnvelope.of(
                DocumentImportRequestedEvent.EVENT_TYPE,
                importId.toString(),
                new DocumentImportRequestedEvent(importId),
                objectMapper
        );
        publishAfterCommit(eventProperties.documentInputImportRequestedTopic(), event);
    }

    public void publishDocumentPublishRequested(UUID importId, List<UUID> candidateIds) {
        PlatformEventEnvelope event = PlatformEventEnvelope.of(
                DocumentPublishRequestedEvent.EVENT_TYPE,
                importId.toString(),
                new DocumentPublishRequestedEvent(importId, candidateIds == null ? List.of() : candidateIds),
                objectMapper
        );
        publishAfterCommit(eventProperties.documentInputPublishRequestedTopic(), event);
    }

    public void publishWebhookAccepted(UUID webhookEventId) {
        PlatformEventEnvelope event = PlatformEventEnvelope.of(
                DocumentWebhookAcceptedEvent.EVENT_TYPE,
                webhookEventId.toString(),
                new DocumentWebhookAcceptedEvent(webhookEventId),
                objectMapper
        );
        publishAfterCommit(eventProperties.documentInputWebhookAcceptedTopic(), event);
    }

    private void publishAfterCommit(String topic, PlatformEventEnvelope event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publish(topic, event, Duration.ZERO);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publish(topic, event, Duration.ZERO);
            }
        });
    }
}
