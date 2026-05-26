package com.songhg.veri.agent.testdesign.application.query;

import com.songhg.veri.agent.common.api.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * WP5 候选列表接口的查询参数。
 */
public class TestDesignCandidatePageRequest extends BasePageRequest {

    /** 任务过滤条件；任务详情路径会优先使用路径中的任务 ID。 */
    @Schema(description = "任务 ID。")
    private UUID taskId;
    /** 项目过滤条件；为空时按调用方权限返回可见候选。 */
    private String projectId;
    /** 需求过滤条件，用于查看某个需求下生成的候选。 */
    @Schema(description = "关联需求 ID。")
    private UUID requirementId;
    /** 候选状态过滤条件，不区分大小写。 */
    private String status;
    /** 覆盖类型过滤条件，不区分大小写。 */
    @Schema(description = "覆盖类型。")
    private String coverageType;
    /** 标题、说明或标签关键字过滤条件。 */
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

    /**
     * 将 Web 查询参数转换为应用层查询对象。
     *
     * @param pathTaskId 任务详情路径中的任务 ID；非空时覆盖 query string 中的 taskId。
     * @return 候选列表查询条件。
     */
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
