package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.common.api.BasePageRequest;
import com.songhg.veri.agent.documentinput.application.DocumentWebhookEventQuery;
import com.songhg.veri.agent.documentinput.domain.WebhookEventStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;

public class WebhookEventPageRequest extends BasePageRequest {

    private UUID sourceId;
    private String sourceCode;
    private String eventType;
    private WebhookEventStatus status;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant receivedFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant receivedTo;

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public WebhookEventStatus getStatus() {
        return status;
    }

    public void setStatus(WebhookEventStatus status) {
        this.status = status;
    }

    public Instant getReceivedFrom() {
        return receivedFrom;
    }

    public void setReceivedFrom(Instant receivedFrom) {
        this.receivedFrom = receivedFrom;
    }

    public Instant getReceivedTo() {
        return receivedTo;
    }

    public void setReceivedTo(Instant receivedTo) {
        this.receivedTo = receivedTo;
    }

    public DocumentWebhookEventQuery toQuery() {
        return new DocumentWebhookEventQuery(
                sourceId,
                trimToNull(sourceCode),
                trimToNull(eventType),
                status,
                receivedFrom,
                receivedTo,
                toPageQuery()
        );
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
