package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WP5 任务级候选质量摘要，用于运营看板和准出判断。
 */
public record TestDesignQualitySummaryResponse(
        @Schema(description = "任务 ID")
        UUID taskId,
        @Schema(description = "所属项目 ID")
        String projectId,
        @Schema(description = "任务标题")
        String taskTitle,
        @Schema(description = "任务状态")
        String taskStatus,
        @Schema(description = "摘要范围，固定为 fullTask")
        String scope,
        @Schema(description = "全部候选数量")
        long total,
        @Schema(description = "可评审候选数量")
        long reviewableCount,
        @Schema(description = "可发布候选数量")
        long publishableCount,
        @Schema(description = "失败候选数量")
        long failedCount,
        @Schema(description = "已确认候选数量")
        long confirmedCount,
        @Schema(description = "已发布候选数量")
        long publishedCount,
        @Schema(description = "步骤动作和预期均完整的候选数量")
        long stepCompleteCount,
        @Schema(description = "最终预期完整的候选数量")
        long expectedCompleteCount,
        @Schema(description = "低置信度候选数量")
        long lowConfidenceCount,
        @Schema(description = "带错误摘要的候选数量")
        long errorCount,
        @Schema(description = "缺少需求关联的候选数量")
        long missingRequirementCount,
        @Schema(description = "标题缺失的候选数量")
        long missingTitleCount,
        @Schema(description = "重复键碰撞候选数量")
        long duplicateKeyCollisionCount,
        @Schema(description = "指标列表")
        List<TestDesignQualityMetricResponse> metrics,
        @Schema(description = "状态分布")
        Map<String, List<TestDesignQualityDistributionItemResponse>> distributions,
        @Schema(description = "生成时间")
        Instant generatedAt
) {
}
