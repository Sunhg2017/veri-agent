package com.songhg.veri.agent.reporting.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class ReportPageRequest extends BasePageRequest {

    @Size(max = 64)
    @Schema(description = "Owning project scope ID")
    private String projectId;

    @Schema(description = "Source WP9 execution run ID")
    private UUID executionRunId;

    @Size(max = 32)
    @Schema(description = "Report lifecycle status")
    private String status;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public UUID getExecutionRunId() {
        return executionRunId;
    }

    public void setExecutionRunId(UUID executionRunId) {
        this.executionRunId = executionRunId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ReportQuery toQuery() {
        return ReportQuery.of(projectId, executionRunId, status, getIndex(), getSize());
    }
}
