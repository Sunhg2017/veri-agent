package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 permission and resource-scope operations summary query parameters.
 */
public class TestDesignScopeSummaryRequest extends BasePageRequest {

    /** Project filter used for permission scope and aggregate isolation. */
    @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
    private String projectId;
    /** Prompt template key filter used to compare scoped operations by generation cohort. */
    @Schema(description = "Prompt 模板标识过滤条件")
    private String promptKey;

    public TestDesignScopeSummaryRequest() {
        setSize(20);
    }

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

    /**
     * Converts the scope summary query to the same bounded task window used by WP5 operations summaries.
     */
    public TestDesignTaskQuery toTaskQuery() {
        return new TestDesignTaskQuery(projectId, null, null, promptKey, toPageQuery());
    }
}
