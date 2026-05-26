package com.songhg.veri.agent.asset.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateApiRequest(
        @Schema(description = "接口或模型调用摘要")
        @NotBlank String summary,
        @Schema(description = "业务说明或补充描述")
        String description,
        @Schema(description = "HTTP 方法")
        @NotBlank String httpMethod,
        @Schema(description = "接口路径")
        @NotBlank String path,
        @Schema(description = "乐观锁或资产版本号，用于并发控制和审计追踪")
        String version,
        @Schema(description = "请求结构定义 JSON")
        String requestSchema,
        @Schema(description = "响应结构定义 JSON")
        String responseSchema,
        @Schema(description = "业务状态")
        String status
) {
}
