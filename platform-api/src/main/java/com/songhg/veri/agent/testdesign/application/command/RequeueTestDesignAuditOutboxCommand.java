package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Requests a bounded replay of WP1 audit outbox events related to one WP5 project scope.
 */
public record RequeueTestDesignAuditOutboxCommand(
        @Schema(description = "所属项目 ID，用于权限校验和 outbox 事件 scope 过滤")
        @NotBlank String projectId,
        @Schema(description = "重放状态：FAILED、DEAD 或 FAILED_OR_DEAD")
        String status,
        @Schema(description = "单次最多重新排队条数，范围 1-100")
        @Min(1) @Max(100) Integer maxItems,
        @Schema(description = "重放原因，服务端会脱敏和截断后写入操作审计")
        String reason
) {
}
