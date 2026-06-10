package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * 资产冲突运营台单条冲突项。
 */
public record TestDesignConflictOperationItemResponse(
        @Schema(description = "任务 ID")
        UUID taskId,
        @Schema(description = "任务标题")
        String taskTitle,
        @Schema(description = "任务状态")
        String taskStatus,
        @Schema(description = "候选 ID")
        UUID candidateId,
        @Schema(description = "候选标题")
        String candidateTitle,
        @Schema(description = "候选当前状态")
        String candidateStatus,
        @Schema(description = "候选当前版本号")
        long candidateVersion,
        @Schema(description = "项目 ID")
        String projectId,
        @Schema(description = "关联需求 ID")
        UUID requirementId,
        @Schema(description = "推荐复用的 WP3 测试用例资产 ID")
        UUID recommendedCaseId,
        @Schema(description = "正式发布冲突记录")
        TestDesignPublishRecordResponse record,
        @Schema(description = "冲突是否已通过后续成功发布或人工链接处理")
        boolean resolved,
        @Schema(description = "当前候选是否仍可由运营台人工链接既有用例")
        boolean resolvable,
        @Schema(description = "冲突发生时间")
        Instant conflictAt
) {
}
