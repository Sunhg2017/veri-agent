package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * WP5 资产冲突运营台查询参数。
 */
public class TestDesignConflictOperationPageRequest extends BasePageRequest {

    @NotBlank
    @Schema(description = "项目 ID，运营台必须限定到单项目资源作用域")
    private String projectId;
    @Schema(description = "任务 ID")
    private UUID taskId;
    @Schema(description = "发布动作编码")
    private String action;
    @Schema(description = "发布结果编码")
    private String result;
    @Schema(description = "候选当前状态")
    private String candidateStatus;
    @Schema(description = "处理状态：OPEN、RESOLVED 或 ALL")
    private String resolutionStatus = "OPEN";
    @Schema(description = "候选标题、任务标题、错误摘要或资产 ID 关键字")
    private String keyword;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getCandidateStatus() {
        return candidateStatus;
    }

    public void setCandidateStatus(String candidateStatus) {
        this.candidateStatus = candidateStatus;
    }

    public String getResolutionStatus() {
        return resolutionStatus;
    }

    public void setResolutionStatus(String resolutionStatus) {
        this.resolutionStatus = resolutionStatus;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public TestDesignConflictOperationQuery toQuery() {
        return new TestDesignConflictOperationQuery(
                trimToNull(projectId),
                taskId,
                trimToNull(action),
                trimToNull(result),
                trimToNull(candidateStatus),
                normalizedResolutionStatus(resolutionStatus),
                trimToNull(keyword),
                toPageQuery()
        );
    }

    private static String normalizedResolutionStatus(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "OPEN";
        }
        return normalized.toUpperCase(java.util.Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
