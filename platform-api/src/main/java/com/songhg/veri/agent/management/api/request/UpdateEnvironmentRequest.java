package com.songhg.veri.agent.management.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateEnvironmentRequest(
        @Schema(description = "名称，用于列表展示和人工识别。")
        @Size(max = 64)
        String name,
        @Schema(description = "环境类型。")
        @Pattern(regexp = "^(|DEV|TEST|STAGING|PREPROD|PROD)$")
        String envType,
        @Schema(description = "Web 访问地址。")
        @Size(max = 512)
        String webUrl,
        @Schema(description = "API 基础地址。")
        @Size(max = 512)
        String apiBaseUrl
) {
}
