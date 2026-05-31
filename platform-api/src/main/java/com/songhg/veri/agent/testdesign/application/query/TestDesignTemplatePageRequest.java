package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 生成模板列表接口的查询参数。
 */
public class TestDesignTemplatePageRequest extends BasePageRequest {

    @Schema(description = "项目 ID；传入后默认返回该项目模板和平台全局模板")
    private String projectId;
    @Schema(description = "是否只返回启用模板")
    private Boolean enabled;
    @Schema(description = "名称、说明或 Prompt 标识关键字")
    private String keyword;
    @Schema(description = "项目过滤时是否包含平台全局模板")
    private Boolean includeGlobal = true;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Boolean getIncludeGlobal() {
        return includeGlobal;
    }

    public void setIncludeGlobal(Boolean includeGlobal) {
        this.includeGlobal = includeGlobal;
    }

    public TestDesignTemplateQuery toQuery() {
        return new TestDesignTemplateQuery(projectId, enabled, keyword, includeGlobal == null || includeGlobal, toPageQuery());
    }
}
