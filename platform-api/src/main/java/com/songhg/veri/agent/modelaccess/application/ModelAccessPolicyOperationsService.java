package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.command.UpsertModelAccessPolicyCommand;
import com.songhg.veri.agent.modelaccess.application.port.ModelAccessRepository;
import com.songhg.veri.agent.modelaccess.application.query.ModelAccessPolicyQuery;
import com.songhg.veri.agent.modelaccess.application.view.ModelAccessEffectivePolicy;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.ModelAccessPolicyOverride;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns WP2 runtime policy operations for non-engineering self-service configuration.
 *
 * <p>Policies are bounded knobs only: model enablement, public-model permission, budget thresholds, overrun behavior and
 * routing group. Resolution is deterministic so invocation, budget checks and the console preview use the same result.
 */
@Service
public class ModelAccessPolicyOperationsService {

    private static final String SCOPE_PLATFORM = "PLATFORM";
    private static final String SCOPE_ROLE = "ROLE";
    private static final String SCOPE_PROJECT = "PROJECT";
    private static final String SCOPE_ENVIRONMENT = "ENVIRONMENT";
    private static final String PLATFORM_SCOPE_KEY = "GLOBAL";

    private final ModelAccessRepository repository;
    private final ModelAccessProperties properties;
    private final ModelAccessActorResolver actorResolver;
    private final AuditLogWriter auditLogWriter;

    public ModelAccessPolicyOperationsService(
            ModelAccessRepository repository,
            ModelAccessProperties properties,
            ModelAccessActorResolver actorResolver,
            AuditLogWriter auditLogWriter
    ) {
        this.repository = repository;
        this.properties = properties;
        this.actorResolver = actorResolver;
        this.auditLogWriter = auditLogWriter;
    }

    @Transactional(readOnly = true)
    public List<ModelAccessPolicyOverride> policies(ModelAccessPolicyQuery query) {
        String scopeType = normalizeScopeType(query == null ? null : query.scopeType(), true);
        String scopeKey = trimToNull(query == null ? null : query.scopeKey());
        return repository.modelAccessPolicies(scopeType, scopeKey);
    }

    /**
     * Creates or replaces one scoped policy with a sanitized, bounded set of runtime knobs.
     */
    @Transactional
    public ModelAccessPolicyOverride upsertPolicy(UpsertModelAccessPolicyCommand command) {
        String scopeType = normalizeScopeType(command.scopeType(), false);
        String scopeKey = normalizeScopeKey(scopeType, command.scopeKey());
        Instant now = Instant.now();
        String actor = currentActor();
        ModelAccessPolicyOverride existing = repository.modelAccessPolicy(scopeType, scopeKey).orElse(null);
        ModelAccessPolicyOverride policy = new ModelAccessPolicyOverride(
                existing == null ? UUID.randomUUID() : existing.id(),
                scopeType,
                scopeKey,
                command.enabled() == null || command.enabled(),
                command.modelInvocationEnabled(),
                command.publicModelAllowed(),
                nonNegativeMoney(command.dailyBudgetLimit(), "dailyBudgetLimit"),
                ratio(command.costAlertWarningRatio()),
                normalizeBudgetOverrunAction(command.budgetOverrunAction()),
                normalizeRoutingGroup(command.routingGroup()),
                sanitizeReason(command.reason()),
                actor,
                existing == null ? now : existing.createdAt(),
                now
        );
        repository.saveModelAccessPolicy(policy);
        auditLogWriter.record(AuditLogWriter.success(
                actorResolver.currentUserPrincipal(),
                "WP2_MODEL_POLICY_UPSERT",
                "MODEL_ACCESS_POLICY",
                scopeType + ":" + scopeKey,
                "WP2 model access runtime policy updated"
        ));
        return policy;
    }

    /**
     * Resolves the policy used by an invocation. Precedence: environment, project, role, platform, deployment default.
     */
    @Transactional(readOnly = true)
    public ModelAccessEffectivePolicy effectivePolicy(ModelInvocationCommand request, ServicePrincipal principal) {
        String projectId = trimToNull(request == null ? null : request.projectId());
        String environmentId = trimToNull(request == null ? null : request.environmentId());
        List<String> roleScopes = normalizeRoles(principal == null ? List.of() : principal.roles());
        List<ModelAccessPolicyOverride> candidates = repository.modelAccessPolicies(null, null)
                .stream()
                .filter(ModelAccessPolicyOverride::enabled)
                .filter(policy -> policyApplies(policy, projectId, environmentId, roleScopes))
                .sorted(Comparator.comparingInt(this::scopePriority))
                .toList();
        EffectiveBuilder builder = new EffectiveBuilder(
                true,
                true,
                null,
                properties.safeCostAlertWarningRatio(),
                properties.safeBudgetOverrunAction(),
                null,
                null,
                null,
                roleScopes.isEmpty() ? null : roleScopes.get(0),
                new ArrayList<>()
        );
        for (ModelAccessPolicyOverride policy : candidates) {
            applyPolicy(builder, policy);
        }
        return new ModelAccessEffectivePolicy(
                builder.modelInvocationEnabled,
                builder.publicModelAllowed,
                builder.dailyBudgetLimit,
                builder.costAlertWarningRatio,
                builder.budgetOverrunAction,
                builder.routingGroup,
                builder.budgetScopeType,
                builder.budgetScopeKey,
                builder.roleScope,
                List.copyOf(builder.matchedScopes),
                true
        );
    }

    @Transactional(readOnly = true)
    public ModelAccessEffectivePolicy effectivePolicy(String projectId, String environmentId, List<String> roles) {
        return effectivePolicy(
                new ModelInvocationCommand(
                        projectId,
                        null,
                        environmentId,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        false,
                        "INTERNAL",
                        null
                ),
                new ServicePrincipal("model-access-policy-preview", null, roles)
        );
    }

    private void applyPolicy(EffectiveBuilder builder, ModelAccessPolicyOverride policy) {
        builder.matchedScopes.add(policy.scopeType() + ":" + policy.scopeKey());
        if (SCOPE_ROLE.equals(policy.scopeType())) {
            builder.roleScope = policy.scopeKey();
        }
        if (policy.modelInvocationEnabled() != null) {
            builder.modelInvocationEnabled = policy.modelInvocationEnabled();
        }
        if (policy.publicModelAllowed() != null) {
            builder.publicModelAllowed = policy.publicModelAllowed();
        }
        if (policy.dailyBudgetLimit() != null) {
            builder.dailyBudgetLimit = policy.dailyBudgetLimit();
            builder.budgetScopeType = policy.scopeType();
            builder.budgetScopeKey = policy.scopeKey();
        }
        if (policy.costAlertWarningRatio() != null) {
            builder.costAlertWarningRatio = policy.costAlertWarningRatio();
        }
        if (StringUtils.hasText(policy.budgetOverrunAction())) {
            builder.budgetOverrunAction = policy.budgetOverrunAction();
        }
        if (StringUtils.hasText(policy.routingGroup())) {
            builder.routingGroup = policy.routingGroup();
        }
    }

    private boolean policyApplies(
            ModelAccessPolicyOverride policy,
            String projectId,
            String environmentId,
            List<String> roleScopes
    ) {
        return switch (policy.scopeType()) {
            case SCOPE_PLATFORM -> PLATFORM_SCOPE_KEY.equals(policy.scopeKey());
            case SCOPE_ROLE -> roleScopes.contains(policy.scopeKey());
            case SCOPE_PROJECT -> policy.scopeKey().equals(projectId);
            case SCOPE_ENVIRONMENT -> policy.scopeKey().equals(environmentId);
            default -> false;
        };
    }

    private int scopePriority(ModelAccessPolicyOverride policy) {
        return switch (policy.scopeType()) {
            case SCOPE_PLATFORM -> 0;
            case SCOPE_ROLE -> 1;
            case SCOPE_PROJECT -> 2;
            case SCOPE_ENVIRONMENT -> 3;
            default -> -1;
        };
    }

    private String normalizeScopeType(String value, boolean allowNull) {
        if (!StringUtils.hasText(value)) {
            if (allowNull) {
                return null;
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "scopeType 不能为空");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case SCOPE_PLATFORM, SCOPE_ROLE, SCOPE_PROJECT, SCOPE_ENVIRONMENT -> normalized;
            default -> throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "scopeType 仅支持 PLATFORM/ROLE/PROJECT/ENVIRONMENT"
            );
        };
    }

    private String normalizeScopeKey(String scopeType, String value) {
        if (SCOPE_PLATFORM.equals(scopeType)) {
            return PLATFORM_SCOPE_KEY;
        }
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "scopeKey 不能为空");
        }
        if (normalized.length() > 128 || !normalized.matches("[A-Za-z0-9_.:@-]+")) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "scopeKey 仅支持 128 位内字母、数字、点、下划线、冒号、@ 和短横线"
            );
        }
        return normalized;
    }

    private BigDecimal nonNegativeMoney(BigDecimal value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能为负数");
        }
        return value;
    }

    private BigDecimal ratio(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "costAlertWarningRatio 必须在 0 到 1 之间");
        }
        return value;
    }

    private String normalizeBudgetOverrunAction(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if ("BLOCK".equals(normalized) || "FALLBACK".equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "budgetOverrunAction 仅支持 BLOCK/FALLBACK");
    }

    private String normalizeRoutingGroup(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > 64 || !normalized.matches("[A-Za-z0-9_.:-]+")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "routingGroup 格式不合法");
        }
        return normalized;
    }

    private String sanitizeReason(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String redacted = SensitiveContentGuard.maskText(normalized);
        return redacted.length() <= 300 ? redacted : redacted.substring(0, 300);
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        List<String> normalized = new ArrayList<>();
        for (String role : roles) {
            String value = trimToNull(role);
            if (value != null && seen.add(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private String currentActor() {
        AuthUserPrincipal principal = actorResolver.currentUserPrincipal();
        if (principal == null || !StringUtils.hasText(principal.username())) {
            return "system";
        }
        return principal.username();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static final class EffectiveBuilder {
        private boolean modelInvocationEnabled;
        private boolean publicModelAllowed;
        private BigDecimal dailyBudgetLimit;
        private BigDecimal costAlertWarningRatio;
        private String budgetOverrunAction;
        private String routingGroup;
        private String budgetScopeType;
        private String budgetScopeKey;
        private String roleScope;
        private final List<String> matchedScopes;

        private EffectiveBuilder(
                boolean modelInvocationEnabled,
                boolean publicModelAllowed,
                BigDecimal dailyBudgetLimit,
                BigDecimal costAlertWarningRatio,
                String budgetOverrunAction,
                String routingGroup,
                String budgetScopeType,
                String budgetScopeKey,
                String roleScope,
                List<String> matchedScopes
        ) {
            this.modelInvocationEnabled = modelInvocationEnabled;
            this.publicModelAllowed = publicModelAllowed;
            this.dailyBudgetLimit = dailyBudgetLimit;
            this.costAlertWarningRatio = costAlertWarningRatio;
            this.budgetOverrunAction = budgetOverrunAction;
            this.routingGroup = routingGroup;
            this.budgetScopeType = budgetScopeType;
            this.budgetScopeKey = budgetScopeKey;
            this.roleScope = roleScope;
            this.matchedScopes = matchedScopes;
        }
    }
}
