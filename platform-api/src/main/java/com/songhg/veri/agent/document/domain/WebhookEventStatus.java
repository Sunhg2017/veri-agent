package com.songhg.veri.agent.document.domain;

public enum WebhookEventStatus {
    ACCEPTED,
    PROCESSING,
    REJECTED,
    PROCESSED,
    FAILED,
    DEAD_LETTER,
    REPLAYED
}
