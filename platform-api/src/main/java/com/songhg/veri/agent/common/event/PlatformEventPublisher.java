package com.songhg.veri.agent.common.event;

import java.time.Duration;

public interface PlatformEventPublisher {

    default void publish(String topic, PlatformEventEnvelope event) {
        publish(topic, event, Duration.ZERO);
    }

    void publish(String topic, PlatformEventEnvelope event, Duration delay);
}
