package com.songhg.veri.agent.testdesign.application.query;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Query parameters for the WP5 cross-WP operations dashboard.
 */
public class TestDesignCrossWpOperationsRequest {

    @Schema(description = "所属项目 ID；为空时只返回平台级聚合，不导出明细标识")
    private String projectId;

    @Schema(description = "Prompt 模板标识过滤条件")
    private String promptKey;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getPromptKey() {
        return promptKey;
    }

    public void setPromptKey(String promptKey) {
        this.promptKey = promptKey;
    }
}
