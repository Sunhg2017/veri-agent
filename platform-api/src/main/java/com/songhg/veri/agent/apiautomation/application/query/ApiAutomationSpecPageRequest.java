package com.songhg.veri.agent.apiautomation.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

public class ApiAutomationSpecPageRequest extends BasePageRequest {

    @Schema(description = "项目 ID 或项目编码")
    private String projectId;

    @Schema(description = "规格状态")
    private String status;

    @Size(max = 128)
    @Schema(description = "名称/版本关键词")
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

    public ApiAutomationSpecQuery toQuery() {
        return new ApiAutomationSpecQuery(
                StringUtils.hasText(projectId) ? projectId.trim() : null,
                StringUtils.hasText(status) ? status.trim().toUpperCase() : null,
                StringUtils.hasText(keyword) ? keyword.trim() : null,
                getSize(),
                offset()
        );
    }
}
