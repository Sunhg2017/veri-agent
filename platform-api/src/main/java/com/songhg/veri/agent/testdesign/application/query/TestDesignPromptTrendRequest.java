package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 Prompt 版本质量趋势查询参数。
 */
public class TestDesignPromptTrendRequest extends BasePageRequest {

    /** 项目过滤条件；为空时按调用方权限返回可见任务 */
    @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
    private String projectId;
    /** Prompt 模板标识过滤条件 */
    @Schema(description = "Prompt 模板标识过滤条件")
    private String promptKey;

    public TestDesignPromptTrendRequest() {
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
     * 将趋势查询转换成任务分页查询，保持 WP5 任务列表一致的过滤和分页口径。
     */
    public TestDesignTaskQuery toTaskQuery() {
        return new TestDesignTaskQuery(projectId, null, null, promptKey, toPageQuery());
    }
}
