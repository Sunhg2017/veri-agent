package com.songhg.veri.agent.notification.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public class NotificationListRequest extends BasePageRequest {

    @Size(max = 16)
    @Schema(description = "Filter by read status: UNREAD, READ")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public NotificationQuery toQuery() {
        return NotificationQuery.of(status, getIndex(), getSize());
    }
}
