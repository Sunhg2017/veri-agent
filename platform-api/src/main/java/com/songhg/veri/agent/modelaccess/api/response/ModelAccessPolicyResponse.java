package com.songhg.veri.agent.modelaccess.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ModelAccessPolicyResponse(
        UUID id,
        String scopeType,
        String scopeKey,
        boolean enabled,
        Boolean modelInvocationEnabled,
        Boolean publicModelAllowed,
        BigDecimal dailyBudgetLimit,
        BigDecimal costAlertWarningRatio,
        String budgetOverrunAction,
        String routingGroup,
        String reason,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt,
        boolean aggregateOnly
) {
}
