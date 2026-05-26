package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 任务列表接口的查询参数
 */
public class TestDesignTaskPageRequest extends BasePageRequest {

    /** 项目过滤条件；为空时按调用方权限返回可见任务 */
    @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
    private String projectId;
    /** 任务状态过滤条件，不区分大小写 */
    private String status;
    /** 标题或需求 ID 关键字过滤条件 */
    @Schema(description = "关键字过滤条件")
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

    /**
     * 将 Web 查询参数转换为应用层查询对象
     */
    public TestDesignTaskQuery toQuery() {
        return new TestDesignTaskQuery(projectId, status, keyword, toPageQuery());
    }
}
