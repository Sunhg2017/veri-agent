package com.songhg.veri.agent.uie2e.application.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.util.StringUtils;

public class UiE2eFlakyMarkPageRequest {

    @Size(max = 64)
    private String projectId;
    private UUID sceneId;
    private UUID runId;
    @Size(max = 32)
    private String status;
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

    public UUID getSceneId() {
        return sceneId;
    }

    public void setSceneId(UUID sceneId) {
        this.sceneId = sceneId;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
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
        this.index = Math.max(index, 0);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = Math.min(Math.max(size, 1), 100);
    }

    public UiE2eFlakyMarkQuery toQuery() {
        return new UiE2eFlakyMarkQuery(
                clean(projectId),
                sceneId,
                runId,
                clean(status),
                clean(keyword),
                (long) index * size,
                size
        );
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
