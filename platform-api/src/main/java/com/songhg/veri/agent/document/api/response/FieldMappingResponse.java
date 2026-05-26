package com.songhg.veri.agent.document.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record FieldMappingResponse(
        @Schema(description = "主键 ID")
        UUID id,
        @Schema(description = "字段映射编码")
        String mappingCode,
        @Schema(description = "名称，用于列表展示和人工识别")
        String name,
        @Schema(description = "来源数据中条目数组的路径")
        String itemPath,
        @Schema(description = "来源数据中标题字段路径")
        String titlePath,
        @Schema(description = "来源数据中描述字段路径")
        String descriptionPath,
        @Schema(description = "来源数据中优先级字段路径")
        String priorityPath,
        @Schema(description = "来源数据中验收标准字段路径")
        String acceptanceCriteriaPath,
        @Schema(description = "来源数据中标签字段路径")
        String tagsPath,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "最近更新时间")
        Instant updatedAt
) {
}
