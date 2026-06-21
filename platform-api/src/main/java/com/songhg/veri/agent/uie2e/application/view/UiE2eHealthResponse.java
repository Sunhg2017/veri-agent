package com.songhg.veri.agent.uie2e.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

public record UiE2eHealthResponse(
        @Schema(description = "服务标识")
        String service,
        @Schema(description = "状态")
        String status,
        @Schema(description = "WP7 UI/E2E 控制面是否启用")
        boolean enabled,
        @Schema(description = "受控 runner 是否启用")
        boolean runnerEnabled,
        @Schema(description = "runner 模式")
        String runnerMode,
        @Schema(description = "默认运行超时秒数")
        int defaultTimeoutSeconds,
        @Schema(description = "最大运行超时秒数")
        int maxTimeoutSeconds,
        @Schema(description = "单次运行允许的最大场景数")
        int maxScenesPerRun,
        @Schema(description = "单实例最大并发数")
        int maxConcurrency,
        @Schema(description = "是否配置了 baseUrl allowlist")
        boolean allowlistEnabled,
        @Schema(description = "allowlist host 数量")
        int allowlistHostCount,
        @Schema(description = "是否允许导出脱敏摘要")
        boolean exportEnabled,
        @Schema(description = "支持的 WP9 节点类型摘要")
        List<String> supportedNodeTypes,
        @Schema(description = "凭据策略摘要")
        Map<String, Object> credentialPolicy,
        @Schema(description = "证据与 artifact 策略摘要")
        Map<String, Object> artifactPolicy,
        @Schema(description = "共享浏览器执行池与批量运行容量摘要")
        Map<String, Object> runnerCapacity,
        @Schema(description = "当前 WP7 功能边界")
        Map<String, Object> policy
) {
}
