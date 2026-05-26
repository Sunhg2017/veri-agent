package com.songhg.veri.agent.management.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record SettingResponse(
        @Schema(description = "配置键")
        String key,
        @Schema(description = "名称，用于列表展示和人工识别")
        String name,
        @Schema(description = "配置值或密钥值；密钥请求中可出现明文，响应必须脱敏")
        String value,
        @Schema(description = "作用域说明或作用域编码")
        String scope,
        @Schema(description = "业务状态")
        String status
) {
}
