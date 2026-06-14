package com.songhg.veri.agent.execution.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

public class ExecutionPlanPageRequest extends BasePageRequest {

    @Schema(description = "Project ID or code")
    private String projectId;

    @Schema(description = "Plan lifecycle status")
    private String status;

    @Size(max = 128)
    @Schema(description = "Name or description keyword")
    private String keyword;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public ExecutionPlanQuery toQuery() {
        return new ExecutionPlanQuery(
                StringUtils.hasText(projectId) ? projectId.trim() : null,
                StringUtils.hasText(status) ? status.trim().toUpperCase() : null,
                StringUtils.hasText(keyword) ? keyword.trim() : null,
                getSize(),
                offset()
        );
    }
}
