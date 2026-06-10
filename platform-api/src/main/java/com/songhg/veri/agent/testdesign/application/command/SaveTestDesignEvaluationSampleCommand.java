package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Creates or updates a curated WP5 evaluation sample.
 */
public record SaveTestDesignEvaluationSampleCommand(
        @Schema(description = "所属项目 ID")
        @NotBlank String projectId,
        @Schema(description = "样本编号；为空时创建接口会生成")
        String sampleKey,
        @Schema(description = "样本标题")
        @NotBlank String title,
        @Schema(description = "来源类型：MANUAL/REVIEW_FEEDBACK/PUBLISHED_CASE/IMPORTED")
        String sourceType,
        @Schema(description = "来源任务 ID")
        UUID sourceTaskId,
        @Schema(description = "来源候选 ID")
        UUID sourceCandidateId,
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "Prompt 版本")
        String promptVersion,
        @Schema(description = "覆盖类型")
        String coverageType,
        @Schema(description = "优先级")
        String priority,
        @Schema(description = "样本状态：CANDIDATE/GOLDEN/FROZEN/DEPRECATED")
        String status,
        @Schema(description = "基线版本")
        String baselineVersion,
        @Schema(description = "脱敏需求摘要，最多 2000 字")
        String requirementSummary,
        @Schema(description = "期望用例轮廓，最多 2000 字")
        String expectedCaseOutline,
        @Schema(description = "断言/校准说明，最多 1000 字")
        String assertionNotes,
        @Schema(description = "标签")
        String tags,
        @Schema(description = "维护备注，最多 1000 字")
        String maintenanceNote
) {
}
