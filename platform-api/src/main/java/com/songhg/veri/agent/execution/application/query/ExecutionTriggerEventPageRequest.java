package com.songhg.veri.agent.execution.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.util.StringUtils;

public class ExecutionTriggerEventPageRequest extends BasePageRequest {

    @Schema(description = "Trigger event status")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ExecutionTriggerEventQuery toQuery(java.util.UUID triggerId) {
        return new ExecutionTriggerEventQuery(
                triggerId,
                StringUtils.hasText(status) ? status.trim().toUpperCase() : null,
                getSize(),
                offset()
        );
    }
}
