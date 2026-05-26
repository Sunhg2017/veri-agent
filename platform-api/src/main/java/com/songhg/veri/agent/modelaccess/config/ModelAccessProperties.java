package com.songhg.veri.agent.modelaccess.config;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "veri-agent.model-access")
public record ModelAccessProperties(
        /** 模型访问模块内部调用令牌。 */
        String serviceToken,
        /** 默认模型名称。 */
        String defaultModel,
        /** 单次请求提示词最大字符数。 */
        int maxPromptChars,
        /** 平台每日成本上限。 */
        BigDecimal dailyPlatformCostLimit,
        /** 项目每日成本上限。 */
        BigDecimal dailyProjectCostLimit,
        /** 预算预估时使用的默认输出 token 数。 */
        int budgetEstimatedOutputTokens,
        /** 成本预算统计时区。 */
        String budgetZoneId,
        /** 成本导出最大行数。 */
        int maxExportRows,
        /** 模型提供方最大重试次数。 */
        int providerMaxRetries,
        /** 熔断打开前允许的连续失败阈值。 */
        int providerCircuitFailureThreshold,
        /** 熔断打开持续时间，单位毫秒。 */
        long providerCircuitOpenMs,
        /** 提供方健康检查缓存 TTL，单位毫秒。 */
        long providerCheckCacheTtlMs,
        /** 成本告警阈值比例。 */
        BigDecimal costAlertWarningRatio,
        /** 提供方限流窗口内最大请求数。 */
        int providerRateLimitMaxRequests,
        /** 提供方限流窗口秒数。 */
        long providerRateLimitWindowSeconds,
        /** 提供方最大并发请求数。 */
        int providerMaxConcurrentRequests,
        /** 异步任务 worker 线程数。 */
        int asyncJobWorkerThreads,
        /** 异步任务派发延迟，单位毫秒。 */
        long asyncJobDispatchDelayMs,
        /** 调用方服务每日成本上限。 */
        BigDecimal dailyCallerServiceCostLimit,
        /** 超预算处理策略，支持 BLOCK 或 FALLBACK。 */
        String budgetOverrunAction,
        /** 模型路由规则列表。 */
        List<RoutingRule> routingRules
) {

    public boolean hasDailyPlatformCostLimit() {
        return dailyPlatformCostLimit != null && dailyPlatformCostLimit.signum() > 0;
    }

    public boolean hasDailyProjectCostLimit() {
        return dailyProjectCostLimit != null && dailyProjectCostLimit.signum() > 0;
    }

    public boolean hasDailyCallerServiceCostLimit() {
        return dailyCallerServiceCostLimit != null && dailyCallerServiceCostLimit.signum() > 0;
    }

    public int safeProviderMaxRetries() {
        return Math.max(0, providerMaxRetries);
    }

    public int safeProviderCircuitFailureThreshold() {
        return Math.max(1, providerCircuitFailureThreshold);
    }

    public long safeProviderCircuitOpenMs() {
        return Math.max(0, providerCircuitOpenMs);
    }

    public long safeProviderCheckCacheTtlMs() {
        return Math.max(0, providerCheckCacheTtlMs);
    }

    public BigDecimal safeCostAlertWarningRatio() {
        if (costAlertWarningRatio == null || costAlertWarningRatio.signum() <= 0) {
            return new BigDecimal("0.8");
        }
        if (costAlertWarningRatio.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return costAlertWarningRatio;
    }

    public int safeProviderRateLimitMaxRequests() {
        return Math.max(0, providerRateLimitMaxRequests);
    }

    public long safeProviderRateLimitWindowSeconds() {
        return Math.max(1, providerRateLimitWindowSeconds);
    }

    public int safeProviderMaxConcurrentRequests() {
        return Math.max(0, providerMaxConcurrentRequests);
    }

    public int safeAsyncJobWorkerThreads() {
        return Math.max(1, asyncJobWorkerThreads);
    }

    public long safeAsyncJobDispatchDelayMs() {
        return Math.max(0, asyncJobDispatchDelayMs);
    }

    public boolean fallbackOnBudgetOverrun() {
        return "FALLBACK".equals(safeBudgetOverrunAction());
    }

    public String safeBudgetOverrunAction() {
        if (budgetOverrunAction == null) {
            return "BLOCK";
        }
        String normalized = budgetOverrunAction.trim().toUpperCase(java.util.Locale.ROOT);
        return "FALLBACK".equals(normalized) ? "FALLBACK" : "BLOCK";
    }

    public List<RoutingRule> safeRoutingRules() {
        return routingRules == null ? List.of() : routingRules;
    }

    public record RoutingRule(
            /** 路由规则名称。 */
            String name,
            /** 适用项目 ID 列表。 */
            List<String> projectIds,
            /** 适用敏感级别列表。 */
            List<String> sensitivityLevels,
            /** 适用调用方服务列表。 */
            List<String> callerServices,
            /** 适用模型能力标签列表。 */
            List<String> capabilities,
            /** 候选提供方分组列表。 */
            List<String> providerGroups,
            /** 成本偏好策略。 */
            String costPreference
    ) {
    }
}
