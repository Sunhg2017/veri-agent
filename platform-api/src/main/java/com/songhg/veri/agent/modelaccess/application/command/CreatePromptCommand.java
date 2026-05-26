package com.songhg.veri.agent.modelaccess.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Application command for creating a prompt template version.
 */
public record CreatePromptCommand(
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "名称，用于列表展示和人工识别")
        String name,
        @Schema(description = "请求或导入内容正文")
        String content,
        @Schema(description = "变更说明")
        String changeNote,
        @Schema(description = "是否高风险变更")
        Boolean highRisk,
        @Schema(description = "是否立即启用")
        Boolean activate
) {
}
