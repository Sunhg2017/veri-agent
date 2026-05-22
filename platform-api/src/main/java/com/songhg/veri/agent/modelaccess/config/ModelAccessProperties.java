package com.songhg.veri.agent.modelaccess.config;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "veri-agent.model-access")
public record ModelAccessProperties(
        String serviceToken,
        String defaultModel,
        int maxPromptChars,
        BigDecimal dailyPlatformCostLimit,
        BigDecimal dailyProjectCostLimit,
        int budgetEstimatedOutputTokens,
        String budgetZoneId,
        int maxExportRows,
        int providerMaxRetries,
        int providerCircuitFailureThreshold,
        long providerCircuitOpenMs,
        long providerCheckCacheTtlMs,
        BigDecimal costAlertWarningRatio,
        int providerRateLimitMaxRequests,
        long providerRateLimitWindowSeconds,
        int providerMaxConcurrentRequests,
        int asyncJobWorkerThreads,
        long asyncJobDispatchDelayMs,
        BigDecimal dailyCallerServiceCostLimit,
        String budgetOverrunAction,
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
            String name,
            List<String> projectIds,
            List<String> sensitivityLevels,
            List<String> callerServices,
            List<String> capabilities,
            List<String> providerGroups,
            String costPreference
    ) {
    }
}
