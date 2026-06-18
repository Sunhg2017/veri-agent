package com.songhg.veri.agent.uie2e.application.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

public class UiE2eScenePageRequest {

    @Size(max = 64)
    private String projectId;
    @Size(max = 64)
    private String applicationId;
    @Size(max = 64)
    private String environmentId;
    @Size(max = 32)
    private String status;
    @Size(max = 32)
    private String riskLevel;
    @Size(max = 32)
    private String tag;
    @Size(max = 128)
    private String keyword;
    @Min(0)
    private int index = 0;
    @Min(1)
    @Max(100)
    private int size = 20;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(String environmentId) {
        this.environmentId = environmentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = Math.max(index, 0);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = Math.min(Math.max(size, 1), 100);
    }

    public UiE2eSceneQuery toQuery() {
        return new UiE2eSceneQuery(
                clean(projectId),
                clean(applicationId),
                clean(environmentId),
                clean(status),
                clean(riskLevel),
                clean(tag),
                clean(keyword),
                (long) index * size,
                size
        );
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
