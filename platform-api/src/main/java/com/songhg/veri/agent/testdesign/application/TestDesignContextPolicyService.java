package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.command.RequestTestDesignContextPolicyOverrideCommand;
import com.songhg.veri.agent.testdesign.application.command.ReviewTestDesignContextPolicyOverrideCommand;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.view.TestDesignContextPolicyEffectiveResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignContextPolicyOverrideResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignContextPolicyOverride;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Manages WP5 context policy overrides and resolves effective clipping limits.
 *
 * <p>Operations are intentionally metadata-only: callers can tune numeric context limits per project/environment and
 * approve those changes, but the service never stores raw policy documents, free-form approval text, strategy diffs or
 * context payloads. This keeps task snapshots reproducible while preserving the export red lines already used by WP5
 * diagnostics and reports.
 */
@Service
public class TestDesignContextPolicyService {

    public static final String SCOPE_PROJECT = "PROJECT";
    public static final String SCOPE_ENVIRONMENT = "ENVIRONMENT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String OPERATION_MODE = "PROJECT_ENVIRONMENT_OVERRIDE";
    public static final String POLICY_RESOLUTION_ORDER = "PLATFORM_DEFAULT_PROJECT_ENVIRONMENT";
    public static final String POLICY_FALLBACK_BEHAVIOR = "FALLBACK_TO_PLATFORM_DEFAULT";
    public static final String APPROVAL_STATUS = "METADATA_APPROVAL_READY";

    private static final int MAX_CONTEXT_ITEMS = 50;
    private static final int MAX_CONTEXT_PREVIEW_CHARS = 2000;
    private static final List<String> ALLOWED_REASON_CODES = List.of(
            "QUALITY_BASELINE",
            "PROJECT_COMPLEXITY",
            "REGULATED_CONTEXT",
            "PROMPT_BUDGET",
            "SMOKE_VALIDATION"
    );

    private final TestDesignRepository repository;
    private final TestDesignPlatformContextClient contextClient;
    private final TestDesignActorResolver actorResolver;
    private final TestDesignProperties properties;

    public TestDesignContextPolicyService(
            TestDesignRepository repository,
            TestDesignPlatformContextClient contextClient,
            TestDesignActorResolver actorResolver,
            TestDesignProperties properties
    ) {
        this.repository = repository;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.properties = properties;
    }

    /**
     * Creates a pending project-level override after validating bounded clipping values and project scope.
     */
    @Transactional
    public TestDesignContextPolicyOverrideResponse requestProjectOverride(
            String projectId,
            RequestTestDesignContextPolicyOverrideCommand command
    ) {
        String scopedProjectId = scopedProjectId(projectId);
        return requestOverride(SCOPE_PROJECT, scopedProjectId, null, command);
    }

    /**
     * Creates a pending environment-level override after validating bounded clipping values and project scope.
     */
    @Transactional
    public TestDesignContextPolicyOverrideResponse requestEnvironmentOverride(
            String projectId,
            String environmentKey,
            RequestTestDesignContextPolicyOverrideCommand command
    ) {
        String scopedProjectId = scopedProjectId(projectId);
        String normalizedEnvironmentKey = requiredCode(environmentKey, "environmentKey");
        return requestOverride(SCOPE_ENVIRONMENT, scopedProjectId, normalizedEnvironmentKey, command);
    }

    /**
     * Approves a pending override and makes it eligible for future task snapshots.
     */
    @Transactional
    public TestDesignContextPolicyOverrideResponse approveOverride(
            UUID id,
            ReviewTestDesignContextPolicyOverrideCommand command
    ) {
        return reviewOverride(id, command, STATUS_APPROVED, "CONTEXT_POLICY_APPROVE");
    }

    /**
     * Rejects a pending override without deleting the operations record.
     */
    @Transactional
    public TestDesignContextPolicyOverrideResponse rejectOverride(
            UUID id,
            ReviewTestDesignContextPolicyOverrideCommand command
    ) {
        return reviewOverride(id, command, STATUS_REJECTED, "CONTEXT_POLICY_REJECT");
    }

    /**
     * Returns sanitized override records for a project and optional environment.
     */
    @Transactional(readOnly = true)
    public List<TestDesignContextPolicyOverrideResponse> overrides(String projectId, String environmentKey) {
        String scopedProjectId = scopedProjectId(projectId);
        String normalizedEnvironmentKey = trimToNull(environmentKey);
        return repository.contextPolicyOverrides(scopedProjectId, normalizedEnvironmentKey).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns the effective policy currently used by new task context snapshots.
     */
    @Transactional(readOnly = true)
    public TestDesignContextPolicyEffectiveResponse effectivePolicy(String projectId, String environmentKey) {
        String scopedProjectId = scopedProjectId(projectId);
        EffectiveContextPolicySnapshot snapshot = effectiveSnapshot(scopedProjectId, trimToNull(environmentKey));
        return new TestDesignContextPolicyEffectiveResponse(
                scopedProjectId,
                trimToNull(environmentKey),
                snapshot.contextLimits(),
                snapshot.appliedOverrideScopes(),
                snapshot.overrideStatusCounts(),
                TestDesignContextAssemblyPolicy.response(snapshot),
                TestDesignContextPolicyGovernance.response(snapshot),
                TestDesignContextPolicyOperations.response(snapshot),
                false,
                false,
                false,
                false,
                true,
                Instant.now()
        );
    }

    /**
     * Resolves effective limits without performing permission checks; callers must already hold project scope.
     */
    @Transactional(readOnly = true)
    public EffectiveContextPolicySnapshot effectiveSnapshotForTask(String projectId, String environmentKey) {
        return effectiveSnapshot(projectId, trimToNull(environmentKey));
    }

    /**
     * Returns platform defaults without repository access for health checks and tests that do not resolve a project.
     */
    public EffectiveContextPolicySnapshot platformDefaultSnapshot() {
        return new EffectiveContextPolicySnapshot(
                properties.effectiveContextLimits(),
                List.of("PLATFORM_DEFAULT"),
                Map.of(),
                false,
                false,
                false,
                false
        );
    }

    private TestDesignContextPolicyOverrideResponse requestOverride(
            String scopeType,
            String projectId,
            String environmentKey,
            RequestTestDesignContextPolicyOverrideCommand command
    ) {
        RequestTestDesignContextPolicyOverrideCommand safeCommand = command == null
                ? new RequestTestDesignContextPolicyOverrideCommand(null, null, null, null, null, null, null)
                : command;
        Map<String, Integer> limits = validatedOverrideLimits(safeCommand);
        if (limits.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "至少需要设置一个上下文策略上限");
        }
        Instant now = Instant.now();
        TestDesignContextPolicyOverride override = new TestDesignContextPolicyOverride(
                UUID.randomUUID(),
                scopeType,
                projectId,
                environmentKey,
                STATUS_PENDING,
                limits.get("linkedAssetsPerRequirement"),
                limits.get("explicitAssetsPerType"),
                limits.get("existingCasesPerRequirement"),
                limits.get("requirementDescriptionChars"),
                limits.get("acceptanceCriteriaChars"),
                limits.get("linkedAssetSchemaChars"),
                reasonCode(safeCommand.changeReasonCode(), "changeReasonCode"),
                null,
                actorResolver.currentActor(),
                null,
                now,
                now
        );
        TestDesignContextPolicyOverride saved = repository.saveContextPolicyOverride(override);
        writeAudit("CONTEXT_POLICY_REQUEST", saved, Map.of(
                "scopeType", saved.scopeType(),
                "projectId", saved.projectId(),
                "environmentScoped", SCOPE_ENVIRONMENT.equals(saved.scopeType()),
                "overrideLimitKeys", overrideLimits(saved).keySet(),
                "changeReasonCodeCaptured", saved.changeReasonCode() != null
        ));
        return toResponse(saved);
    }

    private TestDesignContextPolicyOverrideResponse reviewOverride(
            UUID id,
            ReviewTestDesignContextPolicyOverrideCommand command,
            String nextStatus,
            String auditAction
    ) {
        TestDesignContextPolicyOverride current = repository.contextPolicyOverride(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "上下文策略覆盖不存在: " + id));
        scopedProjectId(current.projectId());
        if (!STATUS_PENDING.equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅 PENDING 策略覆盖可审批: " + current.status());
        }
        String approvalReasonCode = reasonCode(command == null ? null : command.approvalReasonCode(), "approvalReasonCode");
        TestDesignContextPolicyOverride reviewed = new TestDesignContextPolicyOverride(
                current.id(),
                current.scopeType(),
                current.projectId(),
                current.environmentKey(),
                nextStatus,
                current.contextLinkedAssetsPerRequirement(),
                current.contextExplicitAssetsPerType(),
                current.contextExistingCasesPerRequirement(),
                current.contextRequirementDescriptionChars(),
                current.contextAcceptanceCriteriaChars(),
                current.contextAssetSchemaChars(),
                current.changeReasonCode(),
                approvalReasonCode,
                current.requestedBy(),
                actorResolver.currentActor(),
                current.createdAt(),
                Instant.now()
        );
        TestDesignContextPolicyOverride saved = repository.saveContextPolicyOverride(reviewed);
        writeAudit(auditAction, saved, Map.of(
                "scopeType", saved.scopeType(),
                "projectId", saved.projectId(),
                "environmentScoped", SCOPE_ENVIRONMENT.equals(saved.scopeType()),
                "status", saved.status(),
                "approvalReasonCodeCaptured", saved.approvalReasonCode() != null
        ));
        return toResponse(saved);
    }

    private EffectiveContextPolicySnapshot effectiveSnapshot(String projectId, String environmentKey) {
        Map<String, Integer> limits = new LinkedHashMap<>(properties.effectiveContextLimits());
        List<String> appliedScopes = new java.util.ArrayList<>();
        appliedScopes.add("PLATFORM_DEFAULT");
        Optional<TestDesignContextPolicyOverride> projectOverride =
                repository.latestApprovedProjectContextPolicyOverride(projectId);
        projectOverride.ifPresent(override -> {
            applyOverride(limits, override);
            appliedScopes.add(SCOPE_PROJECT);
        });
        Optional<TestDesignContextPolicyOverride> environmentOverride = StringUtils.hasText(environmentKey)
                ? repository.latestApprovedEnvironmentContextPolicyOverride(projectId, environmentKey)
                : Optional.empty();
        environmentOverride.ifPresent(override -> {
            applyOverride(limits, override);
            appliedScopes.add(SCOPE_ENVIRONMENT);
        });
        List<TestDesignContextPolicyOverride> scopedOverrides = repository.contextPolicyOverrides(projectId, environmentKey);
        Map<String, Long> statusCounts = statusCounts(scopedOverrides);
        boolean projectStoreReady = repository.contextPolicyOverrides(projectId, null).stream()
                .anyMatch(override -> SCOPE_PROJECT.equals(override.scopeType()));
        boolean environmentStoreReady = StringUtils.hasText(environmentKey)
                && scopedOverrides.stream().anyMatch(override -> SCOPE_ENVIRONMENT.equals(override.scopeType()));
        return new EffectiveContextPolicySnapshot(
                limits,
                List.copyOf(appliedScopes),
                statusCounts,
                projectStoreReady,
                environmentStoreReady,
                true,
                projectOverride.isPresent() || environmentOverride.isPresent()
        );
    }

    private static Map<String, Long> statusCounts(List<TestDesignContextPolicyOverride> overrides) {
        Map<String, Long> counts = new LinkedHashMap<>();
        overrides.stream()
                .map(TestDesignContextPolicyOverride::status)
                .filter(Objects::nonNull)
                .forEach(status -> counts.merge(status, 1L, Long::sum));
        return counts;
    }

    private static void applyOverride(Map<String, Integer> limits, TestDesignContextPolicyOverride override) {
        apply(limits, "linkedAssetsPerRequirement", override.contextLinkedAssetsPerRequirement());
        apply(limits, "explicitAssetsPerType", override.contextExplicitAssetsPerType());
        apply(limits, "existingCasesPerRequirement", override.contextExistingCasesPerRequirement());
        apply(limits, "requirementDescriptionChars", override.contextRequirementDescriptionChars());
        apply(limits, "acceptanceCriteriaChars", override.contextAcceptanceCriteriaChars());
        apply(limits, "linkedAssetSchemaChars", override.contextAssetSchemaChars());
    }

    private static void apply(Map<String, Integer> limits, String key, Integer value) {
        if (value != null) {
            limits.put(key, value);
        }
    }

    private Map<String, Integer> validatedOverrideLimits(RequestTestDesignContextPolicyOverrideCommand command) {
        Map<String, Integer> limits = new LinkedHashMap<>();
        putIfPresent(limits, "linkedAssetsPerRequirement", command.contextLinkedAssetsPerRequirement(),
                value -> bounded("contextLinkedAssetsPerRequirement", value, MAX_CONTEXT_ITEMS));
        putIfPresent(limits, "explicitAssetsPerType", command.contextExplicitAssetsPerType(),
                value -> bounded("contextExplicitAssetsPerType", value, MAX_CONTEXT_ITEMS));
        putIfPresent(limits, "existingCasesPerRequirement", command.contextExistingCasesPerRequirement(),
                value -> bounded("contextExistingCasesPerRequirement", value, MAX_CONTEXT_ITEMS));
        putIfPresent(limits, "requirementDescriptionChars", command.contextRequirementDescriptionChars(),
                value -> bounded("contextRequirementDescriptionChars", value, MAX_CONTEXT_PREVIEW_CHARS));
        putIfPresent(limits, "acceptanceCriteriaChars", command.contextAcceptanceCriteriaChars(),
                value -> bounded("contextAcceptanceCriteriaChars", value, MAX_CONTEXT_PREVIEW_CHARS));
        putIfPresent(limits, "linkedAssetSchemaChars", command.contextAssetSchemaChars(),
                value -> bounded("contextAssetSchemaChars", value, MAX_CONTEXT_PREVIEW_CHARS));
        return limits;
    }

    private static void putIfPresent(
            Map<String, Integer> limits,
            String key,
            Integer value,
            Function<Integer, Integer> normalizer
    ) {
        if (value != null) {
            limits.put(key, normalizer.apply(value));
        }
    }

    private static int bounded(String fieldName, int value, int maxValue) {
        if (value <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 必须大于 0");
        }
        if (value > maxValue) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能大于 " + maxValue);
        }
        return value;
    }

    private static String reasonCode(String value, String fieldName) {
        String normalized = requiredCode(value, fieldName).toUpperCase(Locale.ROOT);
        if (!ALLOWED_REASON_CODES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不在允许范围内");
        }
        return normalized;
    }

    private static String requiredCode(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能为空");
        }
        String normalized = value.trim();
        if (!normalized.matches("[A-Za-z0-9_.:-]{1,64}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 只能包含字母、数字、点、冒号、下划线或短横线");
        }
        return normalized;
    }

    private String scopedProjectId(String projectId) {
        return contextClient.projectContext(projectId).resourceId();
    }

    private TestDesignContextPolicyOverrideResponse toResponse(TestDesignContextPolicyOverride override) {
        return new TestDesignContextPolicyOverrideResponse(
                override.id(),
                override.scopeType(),
                override.projectId(),
                override.environmentKey(),
                override.status(),
                overrideLimits(override),
                override.changeReasonCode() != null,
                override.approvalReasonCode() != null,
                override.requestedBy(),
                override.approvedBy(),
                override.createdAt(),
                override.updatedAt()
        );
    }

    private static Map<String, Integer> overrideLimits(TestDesignContextPolicyOverride override) {
        Map<String, Integer> limits = new LinkedHashMap<>();
        apply(limits, "linkedAssetsPerRequirement", override.contextLinkedAssetsPerRequirement());
        apply(limits, "explicitAssetsPerType", override.contextExplicitAssetsPerType());
        apply(limits, "existingCasesPerRequirement", override.contextExistingCasesPerRequirement());
        apply(limits, "requirementDescriptionChars", override.contextRequirementDescriptionChars());
        apply(limits, "acceptanceCriteriaChars", override.contextAcceptanceCriteriaChars());
        apply(limits, "linkedAssetSchemaChars", override.contextAssetSchemaChars());
        return limits;
    }

    private void writeAudit(String action, TestDesignContextPolicyOverride override, Map<String, Object> after) {
        contextClient.writeAuditEvent(
                action,
                "TEST_DESIGN_CONTEXT_POLICY_OVERRIDE",
                override.id().toString(),
                override.projectId(),
                "SUCCEEDED",
                after
        );
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record EffectiveContextPolicySnapshot(
            Map<String, Integer> contextLimits,
            List<String> appliedOverrideScopes,
            Map<String, Long> overrideStatusCounts,
            boolean projectOverrideStoreReady,
            boolean environmentOverrideStoreReady,
            boolean changeApprovalWorkflowReady,
            boolean approvedOverrideApplied
    ) {
        public EffectiveContextPolicySnapshot {
            contextLimits = Map.copyOf(contextLimits == null ? Map.of() : contextLimits);
            appliedOverrideScopes = List.copyOf(appliedOverrideScopes == null ? List.of() : appliedOverrideScopes);
            overrideStatusCounts = Map.copyOf(overrideStatusCounts == null ? Map.of() : overrideStatusCounts);
        }
    }
}
