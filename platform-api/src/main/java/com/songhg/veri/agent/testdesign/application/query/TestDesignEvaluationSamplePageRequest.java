package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request parameters for the WP5 evaluation sample maintenance console.
 */
public class TestDesignEvaluationSamplePageRequest extends BasePageRequest {

    @Schema(description = "所属项目 ID，用于权限 scope 和样本隔离")
    private String projectId;
    @Schema(description = "Prompt 模板标识")
    private String promptKey;
    @Schema(description = "Prompt 版本")
    private String promptVersion;
    @Schema(description = "样本状态：CANDIDATE/GOLDEN/FROZEN/DEPRECATED")
    private String status;
    @Schema(description = "覆盖类型")
    private String coverageType;
    @Schema(description = "基线版本")
    private String baselineVersion;
    @Schema(description = "关键词，匹配样本编号、标题、标签或维护备注")
    private String keyword;

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

    public String getBaselineVersion() {
        return baselineVersion;
    }

    public void setBaselineVersion(String baselineVersion) {
        this.baselineVersion = baselineVersion;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public TestDesignEvaluationSampleQuery toQuery() {
        return new TestDesignEvaluationSampleQuery(
                projectId,
                promptKey,
                promptVersion,
                status,
                coverageType,
                baselineVersion,
                keyword,
                toPageQuery()
        );
    }
}
