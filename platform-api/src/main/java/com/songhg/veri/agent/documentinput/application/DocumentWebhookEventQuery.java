package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.documentinput.domain.WebhookEventStatus;

public record DocumentWebhookEventQuery(
        String sourceCode,
        WebhookEventStatus status,
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
