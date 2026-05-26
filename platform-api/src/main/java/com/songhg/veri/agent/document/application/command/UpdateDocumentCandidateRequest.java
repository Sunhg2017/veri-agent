package com.songhg.veri.agent.document.application.command;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateDocumentCandidateRequest(
        @Schema(description = "标题，用于页面展示和关键字检索")
        @NotBlank
        String title,
        @Schema(description = "业务说明或补充描述")
        String description,
        @Schema(description = "优先级")
        String priority,
        @Schema(description = "验收标准")
        String acceptanceCriteria,
        @Schema(description = "标签，多个值用列表或逗号分隔文本表达")
        JsonNode tags,
        @Schema(description = "乐观锁或资产版本号，用于并发控制和审计追踪")
        Long version
) {
}
