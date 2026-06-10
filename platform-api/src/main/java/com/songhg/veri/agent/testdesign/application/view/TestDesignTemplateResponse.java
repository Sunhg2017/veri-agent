package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WP5 用例生成模板的接口出参。
 */
public record TestDesignTemplateResponse(
        @Schema(description = "主键 ID")
        UUID id,
        @Schema(description = "所属项目 ID；为空表示平台全局模板")
        String projectId,
        @Schema(description = "模板名称")
        String name,
        @Schema(description = "模板说明")
        String description,
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "Prompt 模板版本")
        String promptVersion,
        @Schema(description = "默认覆盖类型列表")
        List<String> coverageTypes,
        @Schema(description = "生成策略")
        String generationStrategy,
        @Schema(description = "覆盖策略")
        String coverageStrategy,
        @Schema(description = "每个需求生成的候选数量")
        int caseCountPerRequirement,
        @Schema(description = "上下文默认值，仅包含安全引用和环境键")
        Map<String, Object> contextDefaults,
        @Schema(description = "是否启用")
        boolean enabled,
        @Schema(description = "创建人")
        String createdBy,
        @Schema(description = "最近更新人")
        String updatedBy,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "最近更新时间")
        Instant updatedAt
) {
}
