package com.songhg.veri.agent.integration.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record AuditEventRequest(
        @Schema(description = "操作类型或动作编码。")
        @NotBlank String action,
        @Schema(description = "资源类型。")
        @NotBlank String resourceType,
        @Schema(description = "资源 ID。")
        @NotBlank String resourceId,
        @Schema(description = "权限或配置作用域类型。")
        String scopeType,
        @Schema(description = "权限或配置作用域 ID。")
        String scopeId,
        @Schema(description = "处理结果。")
        @NotBlank String result,
        @Schema(description = "操作原因。")
        String reason,
        @Schema(description = "操作后的资源快照 JSON。")
        Map<String, Object> afterJson
) {
}
