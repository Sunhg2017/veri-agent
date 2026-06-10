package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request parameters for long-term WP5 prompt calibration run history.
 */
public class TestDesignCalibrationRunPageRequest extends BasePageRequest {

    @Schema(description = "所属项目 ID，用于权限 scope 和样本隔离")
    private String projectId;
    @Schema(description = "Prompt 模板标识")
    private String promptKey;
    @Schema(description = "Prompt 版本")
    private String promptVersion;
    @Schema(description = "基线版本")
    private String baselineVersion;
    @Schema(description = "校准状态：PASSED/WARNING/BLOCKED")
    private String status;

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

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getBaselineVersion() {
        return baselineVersion;
    }

    public void setBaselineVersion(String baselineVersion) {
        this.baselineVersion = baselineVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public TestDesignCalibrationRunQuery toQuery() {
        return new TestDesignCalibrationRunQuery(
                projectId,
                promptKey,
                promptVersion,
                baselineVersion,
                status,
                toPageQuery()
        );
    }
}
