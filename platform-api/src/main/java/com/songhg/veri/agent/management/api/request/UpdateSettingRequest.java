package com.songhg.veri.agent.management.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSettingRequest(
        @Schema(description = "名称，用于列表展示和人工识别。")
        @Size(max = 64)
        String name,
        @Schema(description = "配置值或密钥值；密钥请求中可出现明文，响应必须脱敏。")
        @Size(max = 256)
        String value,
        @Schema(description = "权限或配置作用域类型。")
        @Pattern(regexp = "^(|SYSTEM|PROJECT|APPLICATION|ENVIRONMENT)$")
        String scopeType
) {
}
