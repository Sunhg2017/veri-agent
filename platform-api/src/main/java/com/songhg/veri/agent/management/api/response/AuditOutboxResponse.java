package com.songhg.veri.agent.management.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuditOutboxResponse(
        @Schema(description = "主键 ID")
        String id,
        @Schema(description = "链路追踪 ID")
        String traceId,
        @Schema(description = "幂等键，用于重复请求回放和并发去重")
        String idempotencyKey,
        @Schema(description = "业务状态")
        String status,
        @Schema(description = "已重试次数")
        int retryCount,
        @Schema(description = "下次重试时间")
        String nextRetryAt,
        @Schema(description = "锁定时间")
        String lockedAt,
        @Schema(description = "锁定执行者")
        String lockedBy,
        @Schema(description = "最近一次错误摘要")
        String lastError,
        @Schema(description = "审计事件动作")
        String eventAction,
        @Schema(description = "资源类型")
        String resourceType,
        @Schema(description = "资源 ID")
        String resourceId,
        @Schema(description = "处理结果")
        String result,
        @Schema(description = "创建时间")
        String createdAt,
        @Schema(description = "最近更新时间")
        String updatedAt
) {
}
