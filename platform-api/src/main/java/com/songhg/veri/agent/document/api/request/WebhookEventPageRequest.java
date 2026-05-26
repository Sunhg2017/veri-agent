package com.songhg.veri.agent.document.api.request;

import com.songhg.veri.agent.common.api.BasePageRequest;
import com.songhg.veri.agent.document.application.query.DocumentWebhookEventQuery;
import com.songhg.veri.agent.document.domain.WebhookEventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;

public class WebhookEventPageRequest extends BasePageRequest {

    @Schema(description = "文档输入源 ID")
    private UUID sourceId;
    private String sourceCode;
    private String eventType;
    @Schema(description = "业务状态")
    private WebhookEventStatus status;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant receivedFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "接收时间范围结束")
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
