package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record ExecutionHealthResponse(
        @Schema(description = "服务标识")
        String service,
        @Schema(description = "状态")
        String status,
        @Schema(description = "后台调度是否启用")
        boolean schedulerEnabled,
        @Schema(description = "外部 webhook 触发是否启用")
        boolean webhookEnabled,
        @Schema(description = "cron 触发扫描是否启用")
        boolean cronEnabled,
        @Schema(description = "后台调度 tick 间隔毫秒")
        int schedulerIntervalMs,
        @Schema(description = "后台调度启动延迟毫秒")
        int schedulerInitialDelayMs,
        @Schema(description = "后台调度 worker ID")
        String schedulerWorkerId,
        @Schema(description = "单次后台调度 tick 最大认领节点数")
        int schedulerTickBatchSize,
        @Schema(description = "单项目并发 run 上限")
        int maxConcurrentRunsPerProject,
        @Schema(description = "单 run 并发节点上限")
        int maxConcurrentNodesPerRun,
        @Schema(description = "节点 heartbeat 超时秒数")
        int nodeHeartbeatTimeoutSeconds,
        @Schema(description = "默认 run 超时秒数")
        int defaultRunTimeoutSeconds,
        @Schema(description = "恢复扫描批量")
        int recoveryBatchSize,
        @Schema(description = "当前 WP9 功能边界")
        Map<String, Object> policy
) {
}
