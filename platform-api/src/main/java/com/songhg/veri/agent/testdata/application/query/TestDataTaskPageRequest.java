package com.songhg.veri.agent.testdata.application.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.util.StringUtils;

public class TestDataTaskPageRequest {

    @Size(max = 64)
    private String projectId;
    private UUID dataSetId;
    @Size(max = 32)
    private String taskType;
    @Size(max = 32)
    private String status;
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

    public UUID getDataSetId() {
        return dataSetId;
    }

    public void setDataSetId(UUID dataSetId) {
        this.dataSetId = dataSetId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public TestDataTaskQuery toQuery() {
        return new TestDataTaskQuery(
                clean(projectId),
                dataSetId,
                clean(taskType),
                clean(status),
                (long) index * size,
                size
        );
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
