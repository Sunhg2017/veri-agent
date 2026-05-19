package com.songhg.veri.agent.documentinput.domain;

public enum WebhookEventStatus {
    ACCEPTED,
    REJECTED,
    PROCESSED,
    FAILED,
    DEAD_LETTER,
    REPLAYED
}
