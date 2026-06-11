package com.songhg.veri.agent.asset.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SyncOpenApiRequest(
        @Schema(description = "接口摘要")
        @NotBlank String summary,
        @Schema(description = "业务说明或同步说明")
        String description,
        @Schema(description = "HTTP 方法")
        @NotBlank String httpMethod,
        @Schema(description = "接口路径")
        @NotBlank String path,
        @Schema(description = "规格或 schema 版本")
        String version,
        @Schema(description = "请求结构定义 JSON")
        String requestSchema,
        @Schema(description = "响应结构定义 JSON")
        String responseSchema,
        @Schema(description = "所属项目 ID")
        @NotBlank String projectId,
        @Schema(description = "上游来源引用")
        @NotBlank String sourceRef,
        @Schema(description = "业务状态")
        String status
) {
}
