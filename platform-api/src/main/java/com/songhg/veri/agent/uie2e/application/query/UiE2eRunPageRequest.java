package com.songhg.veri.agent.uie2e.application.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

public class UiE2eRunPageRequest {

    private String projectId;
    private UUID sceneId;
    private UUID bundleId;
    private String status;
    private String keyword;
    @Min(0)
    private int index = 0;
    @Min(1)
    @Max(100)
    private int size = 20;

    public UiE2eRunQuery toQuery() {
        return new UiE2eRunQuery(projectId, sceneId, bundleId, status, keyword, index * size, size);
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public UUID getSceneId() {
        return sceneId;
    }

    public void setSceneId(UUID sceneId) {
        this.sceneId = sceneId;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public void setBundleId(UUID bundleId) {
        this.bundleId = bundleId;
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

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
