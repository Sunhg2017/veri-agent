package com.songhg.veri.agent.execution.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.util.StringUtils;

public class ExecutionTriggerPageRequest extends BasePageRequest {

    @Schema(description = "Trigger type: WEBHOOK or CRON")
    private String triggerType;

    @Schema(description = "Trigger status")
    private String status;

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ExecutionTriggerQuery toQuery(java.util.UUID planId) {
        return new ExecutionTriggerQuery(
                planId,
                StringUtils.hasText(triggerType) ? triggerType.trim().toUpperCase() : null,
                StringUtils.hasText(status) ? status.trim().toUpperCase() : null,
                getSize(),
                offset()
        );
    }
}
