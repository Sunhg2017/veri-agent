package com.songhg.veri.agent.apiautomation.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.util.StringUtils;

public class ApiAutomationGenerationTaskPageRequest extends BasePageRequest {

    @Schema(description = "项目 ID 或项目编码")
    private String projectId;

    @Schema(description = "OpenAPI 规格 ID")
    private UUID specId;

    @Size(max = 32)
    @Schema(description = "生成任务状态")
    private String status;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public UUID getSpecId() {
        return specId;
    }

    public void setSpecId(UUID specId) {
        this.specId = specId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ApiAutomationGenerationTaskQuery toQuery() {
        return new ApiAutomationGenerationTaskQuery(
                StringUtils.hasText(projectId) ? projectId.trim() : null,
                specId,
                StringUtils.hasText(status) ? status.trim().toUpperCase() : null,
                getSize(),
                offset()
        );
    }
}
