package com.songhg.veri.agent.modelaccess.application.command;

import java.math.BigDecimal;

public record UpsertModelAccessPolicyCommand(
        String scopeType,
        String scopeKey,
        Boolean enabled,
        Boolean modelInvocationEnabled,
        Boolean publicModelAllowed,
        BigDecimal dailyBudgetLimit,
        BigDecimal costAlertWarningRatio,
        String budgetOverrunAction,
        String routingGroup,
        String reason
) {
}
