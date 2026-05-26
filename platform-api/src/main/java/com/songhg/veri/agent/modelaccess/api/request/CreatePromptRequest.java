package com.songhg.veri.agent.modelaccess.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePromptRequest(
        @Schema(description = "Prompt 模板标识。")
        @NotBlank String promptKey,
        @Schema(description = "名称，用于列表展示和人工识别。")
        @NotBlank String name,
        @Schema(description = "请求或导入内容正文。")
        @NotBlank @Size(max = 12000) String content,
        @Schema(description = "变更说明。")
        String changeNote,
        @Schema(description = "是否高风险变更。")
        Boolean highRisk,
        @Schema(description = "是否立即启用。")
        Boolean activate
) {
}
