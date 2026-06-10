package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.port.ModelAccessRepository;
import com.songhg.veri.agent.modelaccess.application.query.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.view.ModelAccessEffectivePolicy;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;




/**
 * Centralizes WP2 budget-window calculation, projected spend checks and token cost estimation.
 */
@Service
public class ModelInvocationBudgetService {

    private final ModelAccessRepository repository;
    private final ModelAccessProperties properties;

    public ModelInvocationBudgetService(
            ModelAccessRepository repository,
            ModelAccessProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
    }

    BudgetWindow currentWindowIfEnabled() {
        return currentWindowIfEnabled(null);
    }

    BudgetWindow currentWindowIfEnabled(ModelAccessEffectivePolicy policy) {
        if (!budgetCheckEnabled()) {
            if (policy == null || !policy.hasBudgetLimit()) {
                return null;
            }
        }
        try {
            ZoneId zone = StringUtils.hasText(properties.budgetZoneId())
                    ? ZoneId.of(properties.budgetZoneId())
                    : ZoneId.of("Asia/Shanghai");
            LocalDate today = LocalDate.now(zone);
            return new BudgetWindow(
                    today.atStartOfDay(zone).toInstant(),
                    today.plusDays(1).atStartOfDay(zone).toInstant()
            );
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP2 预算时区配置无效");
        }
    }

    /**
     * Checks project, caller-service and platform budgets in the same priority order used by WP2 policy.
     */
    BudgetViolation budgetViolation(
            ModelInvocationCommand request,
            ServicePrincipal principal,
            ModelProviderConfig provider,
            String fullPrompt,
            BudgetWindow window
    ) {
        return budgetViolation(request, principal, provider, fullPrompt, window, null);
    }

    /**
     * Checks runtime policy budgets before deployment defaults so self-service guardrails are enforced immediately.
     */
    BudgetViolation budgetViolation(
            ModelInvocationCommand request,
            ServicePrincipal principal,
            ModelProviderConfig provider,
            String fullPrompt,
            BudgetWindow window,
            ModelAccessEffectivePolicy policy
    ) {
        if (window == null) {
            return null;
        }
        BigDecimal estimatedCost = estimatedCost(provider, fullPrompt);

        if (policy != null && policy.hasBudgetLimit()) {
            BudgetViolation violation = budgetViolation(
                    policy.budgetScopeType() == null ? "POLICY" : policy.budgetScopeType(),
                    policy.dailyBudgetLimit(),
                    estimatedCost,
                    policyBudgetQuery(request, policy, window)
            );
            if (violation != null) {
                return violation;
            }
        }

        if (properties.hasDailyProjectCostLimit()) {
            BudgetViolation violation = budgetViolation(
                    "PROJECT",
                    properties.dailyProjectCostLimit(),
                    estimatedCost,
                    new InvocationQuery(
                            trimToNull(request.projectId()),
                            null,
                            null,
                            null,
                            null,
                            null,
                            window.startTime(),
                            window.endTime(),
                            PageQuery.of(0, 1)
                    )
            );
            if (violation != null) {
                return violation;
            }
        }

        if (properties.hasDailyCallerServiceCostLimit()) {
            String callerService = trimToNull(principal.callerService());
            if (callerService != null) {
                BudgetViolation violation = budgetViolation(
                        "CALLER_SERVICE",
                        properties.dailyCallerServiceCostLimit(),
                        estimatedCost,
                        new InvocationQuery(
                                null,
                                null,
                                null,
                                null,
                                null,
                                callerService,
                                window.startTime(),
                                window.endTime(),
                                PageQuery.of(0, 1)
                        )
                );
                if (violation != null) {
                    return violation;
                }
            }
        }

        if (properties.hasDailyPlatformCostLimit()) {
            return budgetViolation(
                    "PLATFORM",
                    properties.dailyPlatformCostLimit(),
                    estimatedCost,
                    new InvocationQuery(
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            window.startTime(),
                            window.endTime(),
                            PageQuery.of(0, 1)
                    )
            );
        }
        return null;
    }

    private InvocationQuery policyBudgetQuery(
            ModelInvocationCommand request,
            ModelAccessEffectivePolicy policy,
            BudgetWindow window
    ) {
        String scopeType = policy.budgetScopeType();
        String projectId = null;
        String environmentId = null;
        String roleScope = null;
        if ("PROJECT".equals(scopeType)) {
            projectId = trimToNull(request.projectId());
        } else if ("ENVIRONMENT".equals(scopeType)) {
            environmentId = trimToNull(request.environmentId());
        } else if ("ROLE".equals(scopeType)) {
            roleScope = trimToNull(policy.roleScope());
        }
        return new InvocationQuery(
                projectId,
                null,
                environmentId,
                null,
                null,
                null,
                null,
                roleScope,
                window.startTime(),
                window.endTime(),
                PageQuery.of(0, 1)
        );
    }

    BigDecimal estimatedCost(ModelProviderConfig provider, String fullPrompt) {
        return cost(
                provider,
                estimateTokens(fullPrompt),
                Math.max(0, properties.budgetEstimatedOutputTokens())
        );
    }

    BigDecimal actualCost(ModelProviderConfig provider, int inputTokens, int outputTokens) {
        return cost(provider, inputTokens, outputTokens);
    }

    private BudgetViolation budgetViolation(
            String scope,
            BigDecimal limit,
            BigDecimal estimatedCost,
            InvocationQuery query
    ) {
        BigDecimal spent = repository.invocationSummary(query).totalCost();
        if (spent == null) {
            spent = BigDecimal.ZERO;
        }
        BigDecimal projected = spent.add(estimatedCost).setScale(8, RoundingMode.HALF_UP);
        if (projected.compareTo(limit) <= 0) {
            return null;
        }
        return new BudgetViolation(
                "模型调用超出" + scope + "日预算，limit=" + limit
                        + ", spent=" + spent.setScale(8, RoundingMode.HALF_UP)
                        + ", estimated=" + estimatedCost
        );
    }

    private boolean budgetCheckEnabled() {
        return properties.hasDailyPlatformCostLimit()
                || properties.hasDailyProjectCostLimit()
                || properties.hasDailyCallerServiceCostLimit();
    }

    private BigDecimal cost(ModelProviderConfig provider, int inputTokens, int outputTokens) {
        BigDecimal input = provider.inputCostPer1kTokens()
                .multiply(BigDecimal.valueOf(inputTokens))
                .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);
        BigDecimal output = provider.outputCostPer1kTokens()
                .multiply(BigDecimal.valueOf(outputTokens))
                .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);
        return input.add(output).setScale(8, RoundingMode.HALF_UP);
    }

    private int estimateTokens(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    record BudgetWindow(Instant startTime, Instant endTime) {
    }

    record BudgetViolation(String message) {
    }
}
