package com.songhg.veri.agent.document.application.query;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.document.domain.WebhookEventStatus;
import java.time.Instant;
import java.util.UUID;

public record DocumentWebhookEventQuery(
        UUID sourceId,
        String sourceCode,
        String eventType,
        WebhookEventStatus status,
        Instant receivedFrom,
        Instant receivedTo,
        PageQuery pageQuery
) {

    public int index() {
        return pageQuery.index();
    }

    public int size() {
        return pageQuery.size();
    }

    public int offset() {
        return pageQuery.offset();
    }
}
