package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.command.AddTestDesignContextPolicyNoteCommand;
import com.songhg.veri.agent.testdesign.application.command.RequestTestDesignContextPolicyOverrideCommand;
import com.songhg.veri.agent.testdesign.application.command.ReviewTestDesignContextPolicyOverrideCommand;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.view.TestDesignContextPolicyEffectiveResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignContextPolicyNoteResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignContextPolicyOverrideResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignContextPolicyNote;
import com.songhg.veri.agent.testdesign.domain.TestDesignContextPolicyOverride;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
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
 * <p>Operators can maintain bounded policy documents, approval work orders and note timelines, while source context
 * bodies, raw prompts, provider responses and credentials remain prohibited. Task snapshots only persist numeric limits
 * and aggregate governance flags so model payloads and reports do not leak operations details.
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
    public static final String APPROVAL_STATUS = "WORK_ORDER_APPROVAL_READY";

    private static final int MAX_CONTEXT_ITEMS = 50;
    private static final int MAX_CONTEXT_PREVIEW_CHARS = 2000;
    private static final int MAX_POLICY_BODY_CHARS = 4000;
    private static final int MAX_POLICY_NOTE_CHARS = 1000;
    private static final int MAX_WORK_ORDER_KEY_CHARS = 128;
    private static final int MAX_WORK_ORDER_TITLE_CHARS = 256;
    private static final int MAX_WORK_ORDER_URL_CHARS = 512;
    private static final List<String> ALLOWED_REASON_CODES = List.of(
            "QUALITY_BASELINE",
            "PROJECT_COMPLEXITY",
            "REGULATED_CONTEXT",
            "PROMPT_BUDGET",
            "SMOKE_VALIDATION"
    );
    private static final List<String> ALLOWED_WORK_ORDER_STATUSES = List.of(
            "OPEN",
            "IN_REVIEW",
            "APPROVED",
            "REJECTED",
            "CANCELLED"
    );
    private static final List<String> ALLOWED_NOTE_TYPES = List.of(
            "COMMENT",
            "WORK_ORDER"
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
     * Appends an operator note to the approval work order after checking the override project scope.
     */
    @Transactional
    public TestDesignContextPolicyNoteResponse addNote(UUID id, AddTestDesignContextPolicyNoteCommand command) {
        TestDesignContextPolicyOverride override = repository.contextPolicyOverride(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "上下文策略覆盖不存在: " + id));
        scopedProjectId(override.projectId());
        AddTestDesignContextPolicyNoteCommand safeCommand = command == null
                ? new AddTestDesignContextPolicyNoteCommand(null, null)
                : command;
        String noteType = noteType(safeCommand.noteType());
        String noteText = boundedSafeText(safeCommand.noteText(), "noteText", MAX_POLICY_NOTE_CHARS, true, true);
        TestDesignContextPolicyNote saved = appendNote(override.id(), noteType, noteText, actorResolver.currentActor(), Instant.now());
        writeAudit("CONTEXT_POLICY_NOTE_ADD", override, Map.of(
                "scopeType", override.scopeType(),
                "projectId", override.projectId(),
                "noteType", saved.noteType(),
                "noteLength", saved.noteText().length()
        ));
        return toNoteResponse(saved);
    }

    /**
     * Updates a pending override draft before approval. Policy body changes increment the managed body version.
     */
    @Transactional
    public TestDesignContextPolicyOverrideResponse updateOverride(UUID id, RequestTestDesignContextPolicyOverrideCommand command) {
        TestDesignContextPolicyOverride current = repository.contextPolicyOverride(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "上下文策略覆盖不存在: " + id));
        scopedProjectId(current.projectId());
        if (!STATUS_PENDING.equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅 PENDING 策略覆盖可更新草稿: " + current.status());
        }
        RequestTestDesignContextPolicyOverrideCommand safeCommand = command == null
                ? new RequestTestDesignContextPolicyOverrideCommand(
                        null, null, null, null, null, null, null, null, null, null, null, null, null
                )
                : command;
        Map<String, Integer> requestedLimits = validatedOverrideLimits(safeCommand);
        String nextPolicyBody = replacementText(
                safeCommand.policyBody(), current.policyBody(), "policyBody", MAX_POLICY_BODY_CHARS, true
        );
        String nextPolicyDiffSummary = replacementText(
                safeCommand.policyDiffSummary(), current.policyDiffSummary(), "policyDiffSummary", MAX_POLICY_NOTE_CHARS, true
        );
        String nextRequestNote = replacementText(
                safeCommand.requestNote(), current.requestNote(), "requestNote", MAX_POLICY_NOTE_CHARS, true
        );
        String nextWorkOrderTitle = replacementText(
                safeCommand.workOrderTitle(), current.workOrderTitle(), "workOrderTitle", MAX_WORK_ORDER_TITLE_CHARS, false
        );
        String nextWorkOrderUrl = StringUtils.hasText(safeCommand.workOrderUrl())
                ? workOrderUrl(safeCommand.workOrderUrl())
                : current.workOrderUrl();
        String nextWorkOrderKey = StringUtils.hasText(safeCommand.workOrderKey())
                ? workOrderKey(safeCommand.workOrderKey(), current.id())
                : current.workOrderKey();
        String nextChangeReasonCode = StringUtils.hasText(safeCommand.changeReasonCode())
                ? reasonCode(safeCommand.changeReasonCode(), "changeReasonCode")
                : current.changeReasonCode();
        boolean policyBodyChanged = !Objects.equals(current.policyBody(), nextPolicyBody);
        Integer nextPolicyBodyVersion = policyBodyChanged
                ? Math.max(1, current.policyBodyVersion() == null ? 1 : current.policyBodyVersion() + 1)
                : current.policyBodyVersion();
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        TestDesignContextPolicyOverride updated = new TestDesignContextPolicyOverride(
                current.id(),
                current.scopeType(),
                current.projectId(),
                current.environmentKey(),
                current.status(),
                requestedLimits.getOrDefault("linkedAssetsPerRequirement", current.contextLinkedAssetsPerRequirement()),
                requestedLimits.getOrDefault("explicitAssetsPerType", current.contextExplicitAssetsPerType()),
                requestedLimits.getOrDefault("existingCasesPerRequirement", current.contextExistingCasesPerRequirement()),
                requestedLimits.getOrDefault("requirementDescriptionChars", current.contextRequirementDescriptionChars()),
                requestedLimits.getOrDefault("acceptanceCriteriaChars", current.contextAcceptanceCriteriaChars()),
                requestedLimits.getOrDefault("linkedAssetSchemaChars", current.contextAssetSchemaChars()),
                nextChangeReasonCode,
                current.approvalReasonCode(),
                nextWorkOrderKey,
                nextWorkOrderTitle,
                nextWorkOrderUrl,
                current.workOrderStatus(),
                nextPolicyBody,
                sha256OrNull(nextPolicyBody),
                nextPolicyBodyVersion,
                nextPolicyDiffSummary,
                nextRequestNote,
                current.reviewNote(),
                current.requestedBy(),
                current.approvedBy(),
                current.reviewedAt(),
                current.createdAt(),
                now
        );
        TestDesignContextPolicyOverride saved = repository.saveContextPolicyOverride(updated);
        if (StringUtils.hasText(safeCommand.requestNote())) {
            appendNote(saved.id(), "COMMENT", nextRequestNote, actor, now);
        }
        writeAudit("CONTEXT_POLICY_UPDATE", saved, Map.of(
                "scopeType", saved.scopeType(),
                "projectId", saved.projectId(),
                "environmentScoped", SCOPE_ENVIRONMENT.equals(saved.scopeType()),
                "overrideLimitKeys", overrideLimits(saved).keySet(),
                "workOrderKey", saved.workOrderKey(),
                "policyBodyVersion", saved.policyBodyVersion(),
                "policyBodyChanged", policyBodyChanged
        ));
        return toResponse(saved);
    }

    /**
     * Returns the approval work order note timeline for policy operators.
     */
    @Transactional(readOnly = true)
    public List<TestDesignContextPolicyNoteResponse> notes(UUID id) {
        TestDesignContextPolicyOverride override = repository.contextPolicyOverride(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "上下文策略覆盖不存在: " + id));
        scopedProjectId(override.projectId());
        return repository.contextPolicyNotes(id).stream()
                .map(this::toNoteResponse)
                .toList();
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
                ? new RequestTestDesignContextPolicyOverrideCommand(
                        null, null, null, null, null, null, null, null, null, null, null, null, null
                )
                : command;
        Map<String, Integer> limits = validatedOverrideLimits(safeCommand);
        if (limits.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "至少需要设置一个上下文策略上限");
        }
        Instant now = Instant.now();
        UUID overrideId = UUID.randomUUID();
        String actor = actorResolver.currentActor();
        String policyBody = boundedSafeText(safeCommand.policyBody(), "policyBody", MAX_POLICY_BODY_CHARS, true, false);
        String policyDiffSummary = boundedSafeText(
                safeCommand.policyDiffSummary(), "policyDiffSummary", MAX_POLICY_NOTE_CHARS, true, false
        );
        String requestNote = boundedSafeText(safeCommand.requestNote(), "requestNote", MAX_POLICY_NOTE_CHARS, true, false);
        TestDesignContextPolicyOverride override = new TestDesignContextPolicyOverride(
                overrideId,
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
                workOrderKey(safeCommand.workOrderKey(), overrideId),
                boundedSafeText(safeCommand.workOrderTitle(), "workOrderTitle", MAX_WORK_ORDER_TITLE_CHARS, false, false),
                workOrderUrl(safeCommand.workOrderUrl()),
                "OPEN",
                policyBody,
                sha256OrNull(policyBody),
                1,
                policyDiffSummary,
                requestNote,
                null,
                actor,
                null,
                null,
                now,
                now
        );
        TestDesignContextPolicyOverride saved = repository.saveContextPolicyOverride(override);
        if (requestNote != null) {
            appendNote(saved.id(), "REQUEST", requestNote, actor, now);
        }
        writeAudit("CONTEXT_POLICY_REQUEST", saved, Map.of(
                "scopeType", saved.scopeType(),
                "projectId", saved.projectId(),
                "environmentScoped", SCOPE_ENVIRONMENT.equals(saved.scopeType()),
                "overrideLimitKeys", overrideLimits(saved).keySet(),
                "changeReasonCodeCaptured", saved.changeReasonCode() != null,
                "workOrderKey", saved.workOrderKey(),
                "policyBodyDigestCaptured", saved.policyBodyDigest() != null,
                "requestNoteCaptured", saved.requestNote() != null
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
        ReviewTestDesignContextPolicyOverrideCommand safeCommand = command == null
                ? new ReviewTestDesignContextPolicyOverrideCommand(null, null, null)
                : command;
        String approvalReasonCode = reasonCode(safeCommand.approvalReasonCode(), "approvalReasonCode");
        String reviewNote = boundedSafeText(safeCommand.reviewNote(), "reviewNote", MAX_POLICY_NOTE_CHARS, true, false);
        String actor = actorResolver.currentActor();
        Instant now = Instant.now();
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
                current.workOrderKey(),
                current.workOrderTitle(),
                current.workOrderUrl(),
                workOrderStatus(safeCommand.workOrderStatus(), nextStatus),
                current.policyBody(),
                current.policyBodyDigest(),
                current.policyBodyVersion(),
                current.policyDiffSummary(),
                current.requestNote(),
                reviewNote,
                current.requestedBy(),
                actor,
                now,
                current.createdAt(),
                now
        );
        TestDesignContextPolicyOverride saved = repository.saveContextPolicyOverride(reviewed);
        if (reviewNote != null) {
            appendNote(saved.id(), "REVIEW", reviewNote, actor, now);
        }
        writeAudit(auditAction, saved, Map.of(
                "scopeType", saved.scopeType(),
                "projectId", saved.projectId(),
                "environmentScoped", SCOPE_ENVIRONMENT.equals(saved.scopeType()),
                "status", saved.status(),
                "approvalReasonCodeCaptured", saved.approvalReasonCode() != null,
                "workOrderStatus", saved.workOrderStatus(),
                "reviewNoteCaptured", saved.reviewNote() != null
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

    private static String noteType(String value) {
        String normalized = requiredCode(value, "noteType").toUpperCase(Locale.ROOT);
        if (!ALLOWED_NOTE_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "noteType 不在允许范围内");
        }
        return normalized;
    }

    private static String workOrderStatus(String value, String nextApprovalStatus) {
        if (!StringUtils.hasText(value)) {
            return STATUS_APPROVED.equals(nextApprovalStatus) ? "APPROVED" : "REJECTED";
        }
        String normalized = requiredCode(value, "workOrderStatus").toUpperCase(Locale.ROOT);
        if (!ALLOWED_WORK_ORDER_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "workOrderStatus 不在允许范围内");
        }
        return normalized;
    }

    private static String workOrderKey(String value, UUID overrideId) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "WP5-CTX-" + overrideId.toString().substring(0, 8);
        if (normalized.length() > MAX_WORK_ORDER_KEY_CHARS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "workOrderKey 不能大于 " + MAX_WORK_ORDER_KEY_CHARS);
        }
        if (!normalized.matches("[A-Za-z0-9_.:-]{1,128}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "workOrderKey 只能包含字母、数字、点、冒号、下划线或短横线");
        }
        return normalized;
    }

    private static String workOrderUrl(String value) {
        String normalized = boundedSafeText(value, "workOrderUrl", MAX_WORK_ORDER_URL_CHARS, false, false);
        if (normalized == null) {
            return null;
        }
        if (!normalized.matches("https?://[^\\s]+")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "workOrderUrl 仅支持 http 或 https URL");
        }
        return normalized;
    }

    private static String replacementText(
            String nextValue,
            String currentValue,
            String fieldName,
            int maxLength,
            boolean allowNewline
    ) {
        return StringUtils.hasText(nextValue)
                ? boundedSafeText(nextValue, fieldName, maxLength, allowNewline, false)
                : currentValue;
    }

    private static String boundedSafeText(
            String value,
            String fieldName,
            int maxLength,
            boolean allowNewline,
            boolean required
    ) {
        if (!StringUtils.hasText(value)) {
            if (required) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能为空");
            }
            return null;
        }
        String normalized = value.trim();
        if (!allowNewline && (normalized.contains("\n") || normalized.contains("\r"))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能包含换行");
        }
        if (normalized.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能大于 " + maxLength);
        }
        if (TestDesignSensitiveText.containsSensitiveText(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能包含密钥、token 或授权信息");
        }
        return normalized;
    }

    private TestDesignContextPolicyNote appendNote(
            UUID overrideId,
            String noteType,
            String noteText,
            String actor,
            Instant createdAt
    ) {
        TestDesignContextPolicyNote note = new TestDesignContextPolicyNote(
                UUID.randomUUID(),
                overrideId,
                noteType,
                noteText,
                actor,
                createdAt
        );
        return repository.saveContextPolicyNote(note);
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
        List<TestDesignContextPolicyNote> notes = repository.contextPolicyNotes(override.id());
        String latestNotePreview = notes.isEmpty() ? null : notes.get(notes.size() - 1).noteText();
        return new TestDesignContextPolicyOverrideResponse(
                override.id(),
                override.scopeType(),
                override.projectId(),
                override.environmentKey(),
                override.status(),
                overrideLimits(override),
                override.changeReasonCode() != null,
                override.approvalReasonCode() != null,
                override.workOrderKey(),
                override.workOrderTitle(),
                override.workOrderUrl(),
                override.workOrderStatus(),
                override.policyBody(),
                override.policyBodyDigest(),
                override.policyBodyVersion(),
                override.policyDiffSummary(),
                override.requestNote(),
                override.reviewNote(),
                notes.size(),
                latestNotePreview,
                override.requestedBy(),
                override.approvedBy(),
                override.reviewedAt(),
                override.createdAt(),
                override.updatedAt()
        );
    }

    private TestDesignContextPolicyNoteResponse toNoteResponse(TestDesignContextPolicyNote note) {
        return new TestDesignContextPolicyNoteResponse(
                note.id(),
                note.overrideId(),
                note.noteType(),
                note.noteText(),
                note.createdBy(),
                note.createdAt()
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

    private static String sha256OrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
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
