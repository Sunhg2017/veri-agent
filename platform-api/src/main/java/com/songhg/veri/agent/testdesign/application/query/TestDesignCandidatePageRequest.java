package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import java.util.UUID;

public class TestDesignCandidatePageRequest extends BasePageRequest {

    private UUID taskId;
    private String projectId;
    private UUID requirementId;
    private String status;
    private String coverageType;
    private String keyword;

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public UUID getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(UUID requirementId) {
        this.requirementId = requirementId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCoverageType() {
        return coverageType;
    }

    public void setCoverageType(String coverageType) {
        this.coverageType = coverageType;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public TestDesignCandidateQuery toQuery(UUID pathTaskId) {
        return new TestDesignCandidateQuery(
                pathTaskId == null ? taskId : pathTaskId,
                projectId,
                requirementId,
                status,
                coverageType,
                keyword,
                toPageQuery()
        );
    }
}
