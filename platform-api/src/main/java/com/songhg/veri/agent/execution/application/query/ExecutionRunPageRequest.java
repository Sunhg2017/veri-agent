package com.songhg.veri.agent.execution.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.springframework.util.StringUtils;

public class ExecutionRunPageRequest extends BasePageRequest {

    @Schema(description = "Project ID or code")
    private String projectId;

    @Schema(description = "Plan ID")
    private UUID planId;

    @Schema(description = "Run lifecycle status")
    private String status;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public void setPlanId(UUID planId) {
        this.planId = planId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ExecutionRunQuery toQuery() {
        return new ExecutionRunQuery(
                StringUtils.hasText(projectId) ? projectId.trim() : null,
                planId,
                StringUtils.hasText(status) ? status.trim().toUpperCase() : null,
                getSize(),
                offset()
        );
    }
}
