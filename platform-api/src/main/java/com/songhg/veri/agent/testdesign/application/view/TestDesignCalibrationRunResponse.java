package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * WP5 prompt calibration run summary.
 */
public record TestDesignCalibrationRunResponse(
        @Schema(description = "校准运行 ID")
        UUID id,
        @Schema(description = "所属项目 ID")
        String projectId,
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "Prompt 版本")
        String promptVersion,
        @Schema(description = "基线版本")
        String baselineVersion,
        @Schema(description = "运行模式")
        String runMode,
        @Schema(description = "校准状态：PASSED/WARNING/BLOCKED")
        String status,
        @Schema(description = "纳入基线的样本数")
        long sampleCount,
        @Schema(description = "纳入基线的 golden/frozen 样本数")
        long goldenSampleCount,
        @Schema(description = "纳入任务窗口的任务数")
        long taskCount,
        @Schema(description = "纳入任务窗口的候选数")
        long candidateCount,
        @Schema(description = "步骤完整率")
        double stepCompletePercent,
        @Schema(description = "最终预期完整率")
        double expectedCompletePercent,
        @Schema(description = "低置信度率")
        double lowConfidencePercent,
        @Schema(description = "错误率")
        double errorPercent,
        @Schema(description = "重复键碰撞数")
        long duplicateKeyCollisionCount,
        @Schema(description = "人工反馈信号数")
        long feedbackSignalCount,
        @Schema(description = "质量准出状态")
        String readinessStatus,
        @Schema(description = "准出阻断数")
        long readinessBlockingCount,
        @Schema(description = "准出风险数")
        long readinessWarningCount,
        @Schema(description = "相对上一轮的回退项数量")
        long regressionCount,
        @Schema(description = "基线 digest")
        String baselineDigest,
        @Schema(description = "结果 digest")
        String resultDigest,
        @Schema(description = "运行备注")
        String notes,
        @Schema(description = "运行人")
        String runBy,
        @Schema(description = "创建时间")
        Instant createdAt
) {
}
