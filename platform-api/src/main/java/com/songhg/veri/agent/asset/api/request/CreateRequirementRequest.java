package com.songhg.veri.agent.asset.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRequirementRequest(
        @Schema(description = "标题，用于页面展示和关键字检索。")
        @NotBlank String title,
        @Schema(description = "业务说明或补充描述。")
        String description,
        @Schema(description = "业务状态。")
        String status,
        @Schema(description = "优先级。")
        String priority,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离。")
        @NotBlank
        String projectId,
        @Schema(description = "标签，多个值用列表或逗号分隔文本表达。")
        String tags,
        @Schema(description = "数据来源类型或来源系统标识。")
        String source,
        @Schema(description = "来源系统中的外部引用，用于幂等导入和回溯。")
        String sourceRef,
        @Schema(description = "来源系统页面或文档地址。")
        String sourceUrl,
        @Schema(description = "验收标准。")
        String acceptanceCriteria
) {
}
