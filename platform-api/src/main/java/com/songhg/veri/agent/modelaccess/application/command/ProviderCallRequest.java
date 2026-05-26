package com.songhg.veri.agent.modelaccess.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProviderCallRequest(
        @Schema(description = "模型名称。")
        String modelName,
        @Schema(description = "Prompt 内容或模板引用。")
        String prompt,
        @Schema(description = "消息文本。")
        String messageText
) {
}
