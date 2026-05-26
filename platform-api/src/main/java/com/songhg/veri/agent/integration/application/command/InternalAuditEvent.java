package com.songhg.veri.agent.integration.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record InternalAuditEvent(
        @Schema(description = "链路追踪 ID。")
        String traceId,
        @Schema(description = "发起内部调用的服务标识。")
        String actorService,
        @Schema(description = "操作类型或动作编码。")
        String action,
        @Schema(description = "资源类型。")
        String resourceType,
        @Schema(description = "资源 ID。")
        String resourceId,
        @Schema(description = "权限或配置作用域类型。")
        String scopeType,
        @Schema(description = "权限或配置作用域 ID。")
        String scopeId,
        @Schema(description = "处理结果。")
        String result,
        @Schema(description = "操作原因。")
        String reason,
        @Schema(description = "操作后的资源快照 JSON。")
        Map<String, Object> afterJson
) {
}
