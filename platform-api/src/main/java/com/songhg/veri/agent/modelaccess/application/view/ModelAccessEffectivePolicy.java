package com.songhg.veri.agent.modelaccess.application.view;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Effective model policy used by invocation routing and budget checks.
 *
 * <p>Each field is already resolved using runtime overrides first and deployment defaults second. The matched scope list
 * is safe for diagnostics because it contains only scope types and keys, not request payloads or provider secrets.
 */
public record ModelAccessEffectivePolicy(
        boolean modelInvocationEnabled,
        boolean publicModelAllowed,
        BigDecimal dailyBudgetLimit,
        BigDecimal costAlertWarningRatio,
        String budgetOverrunAction,
        String routingGroup,
        String budgetScopeType,
        String budgetScopeKey,
        String roleScope,
        List<String> matchedScopes,
        boolean aggregateOnly
) {

    public boolean hasBudgetLimit() {
        return dailyBudgetLimit != null && dailyBudgetLimit.signum() > 0;
    }

    public boolean fallbackOnBudgetOverrun() {
        return "FALLBACK".equals(budgetOverrunAction);
    }

    public boolean hasRoutingGroupOverride() {
        return StringUtils.hasText(routingGroup);
    }
}
