package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * 更新 WP5 用例生成模板的接口入参。
 */
public record UpdateTestDesignTemplateCommand(
        @Schema(description = "模板名称")
        @NotBlank String name,
        @Schema(description = "模板说明")
        String description,
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "Prompt 模板版本")
        String promptVersion,
        @Schema(description = "默认覆盖类型列表")
        List<String> coverageTypes,
        @Schema(description = "每个需求生成的候选数量")
        Integer caseCountPerRequirement,
        @Schema(description = "上下文默认值，仅支持 environmentKey/contextApiIds/contextPageIds/contextFlowIds")
        Map<String, Object> contextDefaults,
        @Schema(description = "是否启用")
        Boolean enabled
) {
}
