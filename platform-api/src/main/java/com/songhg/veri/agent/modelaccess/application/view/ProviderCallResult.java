package com.songhg.veri.agent.modelaccess.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProviderCallResult(
        @Schema(description = "请求或导入内容正文。")
        String content,
        @Schema(description = "输入 token 数。")
        int inputTokens,
        @Schema(description = "输出 token 数。")
        int outputTokens
) {
}
