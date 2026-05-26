package com.songhg.veri.agent.management.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateApplicationRequest(
        @Schema(description = "名称，用于列表展示和人工识别")
        @Size(max = 64)
        String name,
        @Schema(description = "应用类型")
        @Pattern(regexp = "^(|WEB_ADMIN|HTTP_API|MIXED|OTHER)$")
        String appType,
        @Schema(description = "默认 Web 访问地址")
        @Size(max = 512)
        String defaultWebUrl,
        @Schema(description = "默认 API 基础地址")
        @Size(max = 512)
        String defaultApiBaseUrl,
        @Schema(description = "敏感级别，用于模型调用和数据出域策略")
        @Pattern(regexp = "^(|PUBLIC|INTERNAL|CONFIDENTIAL|STRICT)$")
        String sensitivityLevel,
        @Schema(description = "是否允许使用公网模型处理该范围数据")
        Boolean allowPublicModel
) {
}
