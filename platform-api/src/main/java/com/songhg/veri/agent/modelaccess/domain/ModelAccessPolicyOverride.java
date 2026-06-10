package com.songhg.veri.agent.modelaccess.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Runtime model-access policy override maintained by non-engineering operators.
 *
 * <p>The record intentionally stores only bounded switches, thresholds and scope metadata. It must not contain provider
 * secrets, prompt text, request previews, response previews or free-form model payloads.
 */
public record ModelAccessPolicyOverride(
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
        Instant updatedAt
) {
}
