package com.songhg.veri.agent.common.event;

public interface PlatformEventHandler {

    String eventType();

    void handle(PlatformEventEnvelope event);
}
