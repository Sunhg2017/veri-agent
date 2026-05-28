package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * WP5 候选评审历史的接口出参
 */
public record TestDesignReviewRecordResponse(
        @Schema(description = "主键 ID")
        UUID id,
        @Schema(description = "任务 ID")
        UUID taskId,
        @Schema(description = "候选 ID")
        UUID candidateId,
        @Schema(description = "候选标题预览")
        String title,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
        String projectId,
        @Schema(description = "操作动作")
        String action,
        @Schema(description = "操作前状态")
        String beforeStatus,
        @Schema(description = "操作后状态")
        String afterStatus,
        @Schema(description = "评审人")
        String reviewer,
        @Schema(description = "是否包含评审说明")
        boolean hasComment,
        @Schema(description = "脱敏后的评审说明预览")
        String commentPreview,
        @Schema(description = "变更字段摘要")
        List<String> changedFields,
        @Schema(description = "操作前候选版本")
        Long versionBefore,
        @Schema(description = "操作后候选版本")
        Long versionAfter,
        @Schema(description = "创建时间")
        Instant createdAt
) {
}
