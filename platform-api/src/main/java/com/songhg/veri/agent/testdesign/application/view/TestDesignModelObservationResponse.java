package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * WP5 task-level model invocation observation exposed to operators.
 *
 * <p>The response is intentionally limited to routing, cost and latency metadata. Prompt text, request previews,
 * response previews and model raw content stay inside WP2 audit storage and are never copied into the WP5 task view.
 */
public record TestDesignModelObservationResponse(
        @Schema(description = "模型调用记录 ID")
        UUID invocationId,
        @Schema(description = "关联的异步模型调用任务 ID，存在时可通过 traceId 串联排障")
        UUID jobId,
        @Schema(description = "异步调用链路追踪 ID")
        String traceId,
        @Schema(description = "调用日志是否可用")
        Boolean available,
        @Schema(description = "模型调用状态")
        String status,
        @Schema(description = "模型供应商名称")
        String providerName,
        @Schema(description = "模型名称")
        String modelName,
        @Schema(description = "命中的模型路由规则")
        String routingRuleName,
        @Schema(description = "路由分组")
        String routingGroup,
        @Schema(description = "模型能力")
        String modelCapability,
        @Schema(description = "是否使用 fallback")
        Boolean fallbackUsed,
        @Schema(description = "输入 token 数")
        Integer inputTokens,
        @Schema(description = "输出 token 数")
        Integer outputTokens,
        @Schema(description = "总成本")
        BigDecimal totalCost,
        @Schema(description = "调用耗时，单位毫秒")
        Long latencyMs,
        @Schema(description = "失败错误码")
        String errorCode,
        @Schema(description = "脱敏错误摘要")
        String errorMessage,
        @Schema(description = "发起模型调用的服务编码")
        String actorService,
        @Schema(description = "模型调用创建时间")
        Instant createdAt
) {
}
