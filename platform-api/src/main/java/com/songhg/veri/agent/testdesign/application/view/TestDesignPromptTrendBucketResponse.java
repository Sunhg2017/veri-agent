package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * WP5 Prompt 版本质量趋势的单个版本聚合桶。
 */
public record TestDesignPromptTrendBucketResponse(
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "Prompt 模板版本")
        String promptVersion,
        @Schema(description = "聚合任务数量")
        long taskCount,
        @Schema(description = "候选总数")
        long candidateCount,
        @Schema(description = "已确认候选数量")
        long confirmedCount,
        @Schema(description = "已发布候选数量")
        long publishedCount,
        @Schema(description = "步骤完整候选数量")
        long stepCompleteCount,
        @Schema(description = "最终预期完整候选数量")
        long expectedCompleteCount,
        @Schema(description = "低置信度候选数量")
        long lowConfidenceCount,
        @Schema(description = "错误候选数量")
        long errorCount,
        @Schema(description = "重复键碰撞候选数量")
        long duplicateKeyCollisionCount,
        @Schema(description = "人工修正候选数量")
        long correctionCount,
        @Schema(description = "人工驳回候选数量")
        long rejectedCount,
        @Schema(description = "人工忽略候选数量")
        long ignoredCount,
        @Schema(description = "步骤完整率，百分比")
        double stepCompletePercent,
        @Schema(description = "最终预期完整率，百分比")
        double expectedCompletePercent,
        @Schema(description = "低置信度率，百分比")
        double lowConfidencePercent,
        @Schema(description = "错误率，百分比")
        double errorPercent,
        @Schema(description = "人工反馈率，百分比")
        double feedbackSignalPercent,
        @Schema(description = "该 Prompt 版本按当前 WP5 准出阈值计算的聚合准出摘要")
        TestDesignQualityReadinessResponse readiness,
        @Schema(description = "该版本最近任务创建时间")
        Instant latestTaskCreatedAt
) {
}
