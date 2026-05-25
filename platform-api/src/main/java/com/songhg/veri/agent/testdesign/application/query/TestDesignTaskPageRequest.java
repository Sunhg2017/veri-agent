package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;

public class TestDesignTaskPageRequest extends BasePageRequest {

    private String projectId;
    private String status;
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

    public TestDesignTaskQuery toQuery() {
        return new TestDesignTaskQuery(projectId, status, keyword, toPageQuery());
    }
}
