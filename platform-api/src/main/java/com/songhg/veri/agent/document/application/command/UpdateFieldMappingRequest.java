package com.songhg.veri.agent.document.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFieldMappingRequest(
        @Schema(description = "名称，用于列表展示和人工识别。")
        @Size(max = 128)
        String name,
        @Schema(description = "来源数据中条目数组的路径。")
        String itemPath,
        @Schema(description = "来源数据中标题字段路径。")
        @NotBlank
        String titlePath,
        @Schema(description = "来源数据中描述字段路径。")
        String descriptionPath,
        @Schema(description = "来源数据中优先级字段路径。")
        String priorityPath,
        @Schema(description = "来源数据中验收标准字段路径。")
        String acceptanceCriteriaPath,
        @Schema(description = "来源数据中标签字段路径。")
        String tagsPath
) {
}
