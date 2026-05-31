package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.event.PlatformEventEnvelope;
import com.songhg.veri.agent.common.event.PlatformEventProperties;
import com.songhg.veri.agent.common.event.PlatformEventPublisher;
import com.songhg.veri.agent.testdesign.application.event.TestDesignGenerationRequestedEvent;
import com.songhg.veri.agent.testdesign.application.event.TestDesignPublishRequestedEvent;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TestDesignEventPublisher {

    private final PlatformEventPublisher eventPublisher;
    private final PlatformEventProperties eventProperties;
    private final ObjectMapper objectMapper;

    public TestDesignEventPublisher(
            @Lazy PlatformEventPublisher eventPublisher,
            PlatformEventProperties eventProperties,
            ObjectMapper objectMapper
    ) {
        this.eventPublisher = eventPublisher;
        this.eventProperties = eventProperties;
        this.objectMapper = objectMapper;
    }

    public void publishGenerationRequested(UUID taskId) {
        PlatformEventEnvelope event = PlatformEventEnvelope.of(
                TestDesignGenerationRequestedEvent.EVENT_TYPE,
                taskId.toString(),
                new TestDesignGenerationRequestedEvent(taskId),
                objectMapper
        );
        publishAfterCommit(eventProperties.testDesignGenerationRequestedTopic(), event);
    }

    public void publishPublishRequested(UUID taskId, List<UUID> candidateIds) {
        PlatformEventEnvelope event = PlatformEventEnvelope.of(
                TestDesignPublishRequestedEvent.EVENT_TYPE,
                taskId.toString(),
                new TestDesignPublishRequestedEvent(taskId, candidateIds == null ? List.of() : candidateIds),
                objectMapper
        );
        publishAfterCommit(eventProperties.testDesignPublishRequestedTopic(), event);
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
