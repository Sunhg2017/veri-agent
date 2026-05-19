package com.songhg.veri.agent.documentinput.api.request;

import com.songhg.veri.agent.common.api.BasePageRequest;
import com.songhg.veri.agent.documentinput.domain.WebhookEventStatus;

public class WebhookEventPageRequest extends BasePageRequest {

    private String sourceCode;
    private WebhookEventStatus status;

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public WebhookEventStatus getStatus() {
        return status;
    }

    public void setStatus(WebhookEventStatus status) {
        this.status = status;
    }
}
