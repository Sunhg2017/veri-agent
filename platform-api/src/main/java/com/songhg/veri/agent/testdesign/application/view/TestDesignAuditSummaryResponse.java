package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * WP5 任务本域审计链摘要，只聚合任务、评审记录和发布记录。
 */
public record TestDesignAuditSummaryResponse(
        @Schema(description = "任务 ID")
        UUID taskId,
        @Schema(description = "所属项目 ID")
        String projectId,
        @Schema(description = "任务状态")
        String taskStatus,
        @Schema(description = "任务创建人")
        String requestedBy,
        @Schema(description = "任务创建时间")
        Instant taskCreatedAt,
        @Schema(description = "任务最近更新时间")
        Instant taskUpdatedAt,
        @Schema(description = "本域审计事件总数")
        long eventCount,
        @Schema(description = "人工评审记录数")
        long reviewRecordCount,
        @Schema(description = "发布记录数")
        long publishRecordCount,
        @Schema(description = "dryRun 预演记录数")
        long dryRunRecordCount,
        @Schema(description = "失败或冲突记录数")
        long issueCount,
        @Schema(description = "包含人工说明或错误摘要的记录数")
        long noteCoverageCount,
        @Schema(description = "最近事件列表")
        List<TestDesignAuditTimelineItemResponse> recentEvents,
        @Schema(description = "审计聚合指标")
        List<TestDesignAuditSummaryMetricResponse> metrics,
        @Schema(description = "生成时间")
        Instant generatedAt
) {
}
