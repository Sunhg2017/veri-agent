package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Sanitized WP5 evaluation sample view for the maintenance console.
 */
public record TestDesignEvaluationSampleResponse(
        @Schema(description = "样本 ID")
        UUID id,
        @Schema(description = "所属项目 ID")
        String projectId,
        @Schema(description = "样本编号")
        String sampleKey,
        @Schema(description = "样本标题")
        String title,
        @Schema(description = "来源类型")
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
        @Schema(description = "样本状态")
        String status,
        @Schema(description = "基线版本")
        String baselineVersion,
        @Schema(description = "脱敏需求摘要")
        String requirementSummary,
        @Schema(description = "期望用例轮廓")
        String expectedCaseOutline,
        @Schema(description = "断言/校准说明")
        String assertionNotes,
        @Schema(description = "标签")
        String tags,
        @Schema(description = "维护备注")
        String maintenanceNote,
        @Schema(description = "样本摘要 digest")
        String sampleDigest,
        @Schema(description = "敏感信息扫描状态")
        String sensitiveScanStatus,
        @Schema(description = "创建人")
        String createdBy,
        @Schema(description = "更新人")
        String updatedBy,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "更新时间")
        Instant updatedAt
) {
}
