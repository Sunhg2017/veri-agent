package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.command.CreateTestDesignTaskCommand;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskQuery;
import com.songhg.veri.agent.testdesign.application.view.TestDesignHealthResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishRecordResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskDetailResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.CoverageType;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TestDesignTaskService {

    private static final Logger log = LoggerFactory.getLogger(TestDesignTaskService.class);
    private static final List<String> DEFAULT_COVERAGE_TYPES = List.of("SMOKE", "FUNCTIONAL", "EXCEPTION");
    private static final Pattern PROMPT_REF_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]+");
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final Set<String> RETRYABLE_TASK_STATUSES = Set.of(
            TestDesignTaskStatus.FAILED.name(),
            TestDesignTaskStatus.PARTIAL_SUCCESS.name(),
            TestDesignTaskStatus.CANCELLED.name()
    );
    private static final Set<String> CANCELLABLE_TASK_STATUSES = Set.of(
            TestDesignTaskStatus.DRAFT.name(),
            TestDesignTaskStatus.QUEUED.name(),
            TestDesignTaskStatus.RUNNING.name(),
            TestDesignTaskStatus.PARTIAL_SUCCESS.name(),
            TestDesignTaskStatus.FAILED.name()
    );
    private final TestDesignRepository repository;
    private final TestDesignEventPublisher eventPublisher;
    private final AssetService assetService;
    private final TestDesignPlatformContextClient contextClient;
    private final TestDesignActorResolver actorResolver;
    private final TestDesignResponseMapper responseMapper;
    private final TestDesignGenerationService generationService;
    private final TestDesignContextPolicyService contextPolicyService;
    private final TestDesignTemplateService templateService;
    private final TestDesignProperties properties;
    private final ObjectMapper objectMapper;

    public TestDesignTaskService(
            TestDesignRepository repository,
            TestDesignEventPublisher eventPublisher,
            AssetService assetService,
            TestDesignPlatformContextClient contextClient,
            TestDesignActorResolver actorResolver,
            TestDesignResponseMapper responseMapper,
            TestDesignGenerationService generationService,
            TestDesignContextPolicyService contextPolicyService,
            TestDesignTemplateService templateService,
            TestDesignProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.assetService = assetService;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.responseMapper = responseMapper;
        this.generationService = generationService;
        this.contextPolicyService = contextPolicyService;
        this.templateService = templateService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public TestDesignHealthResponse health() {
        return new TestDesignHealthResponse(
                "test-design",
                "UP",
                properties.generationEnabled(),
                properties.generationMode(),
                properties.promptKey(),
                properties.promptVersion(),
                maxRequirementsPerTask(),
                maxCasesPerRequirement(),
                properties.effectiveContextLimits(),
                TestDesignContextAssemblyPolicy.response(),
                TestDesignContextPolicyGovernance.response(),
                TestDesignContextPolicyOperations.response(),
                TestDesignGenerationOrchestrationPolicy.response(properties, orchestrationRuntimeSnapshot(Instant.now())),
                TestDesignScopePolicy.response(),
                TestDesignEvaluationCorpusPolicy.response(),
                TestDesignReleaseReadinessPolicy.response(properties),
                TestDesignAuditChainPolicy.response(),
                TestDesignModelObservationPolicy.response(),
                TestDesignArchivePolicy.response(properties),
                TestDesignReportManifestPolicy.response(),
                CoverageType.codes().stream().sorted().toList()
        );
    }

    public PageResponse<TestDesignTaskResponse> tasks(TestDesignTaskQuery query) {
        validateProjectWhenProvided(query.projectId());
        List<TestDesignTaskResponse> items = repository.tasks(query).stream()
                .map(responseMapper::toTaskResponse)
                .toList();
        return PageResponse.of(items, query.index(), query.size(), repository.countTasks(query));
    }

    public TestDesignTaskDetailResponse task(UUID id) {
        TestDesignTask task = taskOrThrow(id);
        List<TestDesignCandidate> candidates = repository.candidatesByTask(id);
        Map<UUID, TestDesignCandidate> candidateById = candidateById(candidates);
        List<TestDesignPublishRecordResponse> records = repository.publishRecords(id).stream()
                .map(record -> responseMapper.toPublishRecordResponse(record, candidateById.get(record.candidateId())))
                .toList();
        return new TestDesignTaskDetailResponse(
                responseMapper.toTaskResponse(task),
                candidates.stream().map(responseMapper::toCandidateResponse).toList(),
                records
        );
    }

    /**
     * Returns task metadata without loading candidate rows so paginated workbench refreshes stay bounded.
     */
    public TestDesignTaskResponse taskSummary(UUID id) {
        return responseMapper.toTaskResponse(taskOrThrow(id));
    }

    /**
     * Creates a task and either queues or immediately claims generation.
     *
     * <p>Task identity, idempotency, initial status and audit evidence stay in this service. The generation service
     * only prepares the redacted context and candidate batch, which keeps the WP5 application layer split by actual
     * responsibility instead of routing through a renamed facade.
     */
    @Transactional
    public TestDesignTaskDetailResponse createTask(CreateTestDesignTaskCommand command) {
        return createTask(command, null);
    }

    /**
     * Creates a project-scoped task with idempotency replay protection.
     *
     * <p>The request digest is stored with the idempotency key so an accidental key reuse with a different payload
     * returns a stable conflict instead of silently replaying the wrong generated candidates.
     */
    @Transactional
    public TestDesignTaskDetailResponse createTask(CreateTestDesignTaskCommand command, String idempotencyHeader) {
        if (!properties.generationEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP5 用例生成未启用");
        }
        String projectId = contextClient.projectContext(command.projectId()).resourceId();
        String idempotencyKey = resolveIdempotencyKey(command.idempotencyKey(), idempotencyHeader);
        if (StringUtils.hasText(idempotencyKey)) {
            // DB profile uses a transaction-scoped advisory lock so concurrent retries replay instead of racing the unique index.
            repository.lockTaskIdempotencyKey(projectId, idempotencyKey);
        }
        List<UUID> requirementIds = distinctRequirementIds(command.requirementIds());
        if (requirementIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "生成任务必须选择需求");
        }
        if (requirementIds.size() > maxRequirementsPerTask()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "单次生成最多支持 " + maxRequirementsPerTask() + " 个需求");
        }
        EffectiveTaskGenerationConfig generationConfig = effectiveGenerationConfig(command, projectId);
        TestDesignGenerationService.ExplicitContextAssetIds explicitContext = generationConfig.explicitContext();
        TestDesignContextPolicyService.EffectiveContextPolicySnapshot effectivePolicy =
                contextPolicyService.effectiveSnapshotForTask(projectId, generationConfig.environmentKey());
        validateExplicitContextLimit(explicitContext, effectivePolicy);
        List<String> generationCoverageTypes = generationConfig.coverageTypes().stream()
                .limit(generationConfig.caseCountPerRequirement())
                .toList();
        String requestDigest = taskRequestDigest(
                projectId,
                command.title(),
                requirementIds,
                explicitContext,
                generationConfig.environmentKey(),
                effectivePolicy,
                generationConfig.coverageTypes(),
                generationConfig.caseCountPerRequirement(),
                generationConfig.promptKey(),
                generationConfig.promptVersion(),
                generationConfig.templateId(),
                generationConfig.templateName()
        );
        Optional<TestDesignTask> replayedTask = replayIdempotentTaskIfPresent(projectId, idempotencyKey, requestDigest);
        if (replayedTask.isPresent()) {
            return task(replayedTask.get().id());
        }
        List<RequirementResponse> requirements = requirementIds.stream()
                .map(assetService::getRequirement)
                .peek(requirement -> ensureSameProject(requirement, projectId))
                .toList();
        TestDesignGenerationService.TestDesignGenerationContext generationContext =
                generationService.generationContext(projectId, requirements, explicitContext,
                        generationConfig.environmentKey(), effectivePolicy, generationConfig.promptKey(),
                        generationConfig.promptVersion(), templateSummary(generationConfig));
        Instant now = Instant.now();
        String title = taskTitle(command.title(), requirements);
        UUID taskId = UUID.randomUUID();
        String requestedBy = actorResolver.currentActor();
        TestDesignTask task = new TestDesignTask(
                taskId,
                projectId,
                title,
                initialTaskStatus().name(),
                idsText(requirementIds),
                String.join(",", generationCoverageTypes),
                generationConfig.promptKey(),
                generationConfig.promptVersion(),
                null,
                null,
                properties.generationMode(),
                requirements.size(),
                0,
                0,
                0,
                null,
                requestedBy,
                idempotencyKey,
                requestDigest,
                generationContext.inputDigest(),
                generationContext.contextSummaryJson(),
                now,
                now
        );
        repository.saveTask(task);
        writeAudit("CREATE", "TEST_DESIGN_TASK", taskId, projectId, taskAuditDetails(
                taskId,
                requirements.size(),
                0,
                generationCoverageTypes,
                idempotencyKey,
                generationContext.inputDigest(),
                explicitContext,
                generationConfig
        ));
        if (properties.asyncGenerationEnabled()) {
            eventPublisher.publishGenerationRequested(taskId);
            writeAudit("GENERATION_QUEUED", "TEST_DESIGN_TASK", taskId, projectId, Map.of(
                    "taskId", taskId,
                    "status", task.status(),
                    "requirementCount", requirements.size()
            ));
        } else {
            generateQueuedTask(taskId, TestDesignTaskStatus.RUNNING);
        }
        return task(taskId);
    }

    /**
     * Retries a failed generation task without deleting reviewed candidates.
     *
     * <p>Retry is a task-state transition first. It reuses the generation service for candidate production, then this
     * service merges only missing duplicate keys so manual review work survives retries.
     */
    @Transactional
    public TestDesignTaskDetailResponse retryTask(UUID id) {
        if (!properties.generationEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP5 用例生成未启用");
        }
        TestDesignTask task = taskOrThrow(id);
        if (!RETRYABLE_TASK_STATUSES.contains(task.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前任务状态不可重试: " + task.status());
        }
        List<UUID> requirementIds = requirementIdsFromText(task.requirementIds());
        if (requirementIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "任务缺少需求上下文，无法重试");
        }
        TestDesignTask running = withTaskStatus(task, TestDesignTaskStatus.RUNNING, null);
        repository.saveTask(running);

            List<String> coverageTypes = normalizedCoverageTypes(csvValues(task.coverageTypes()));
        List<TestDesignCandidate> existingCandidates = repository.candidatesByTask(id);
        Set<String> existingDuplicateKeys = existingCandidates.stream()
                .map(TestDesignCandidate::duplicateKey)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        List<RequirementResponse> requirements = requirementIds.stream()
                .map(assetService::getRequirement)
                .peek(requirement -> ensureSameProject(requirement, task.projectId()))
                .toList();
        try {
            TestDesignGenerationService.GenerationAttempt attempt =
                    generationService.generateCandidates(running, requirements, coverageTypes, Instant.now());
            List<TestDesignCandidate> createdCandidates = new ArrayList<>();
            for (TestDesignCandidate candidate : attempt.candidates()) {
                if (existingDuplicateKeys.add(candidate.duplicateKey())) {
                    repository.saveCandidate(candidate);
                    createdCandidates.add(candidate);
                }
            }

            List<TestDesignCandidate> mergedCandidates = new ArrayList<>(existingCandidates);
            mergedCandidates.addAll(createdCandidates);
            TestDesignTask finished = withTaskCounts(
                    withTaskStatus(attempt.task(), TestDesignTaskStatus.SUCCEEDED, attempt.warningMessage()),
                    mergedCandidates
            );
            repository.saveTask(finished);
            writeAudit("RETRY", "TEST_DESIGN_TASK", id, task.projectId(), Map.of(
                    "taskId", id,
                    "createdCandidateCount", createdCandidates.size(),
                    "totalCandidateCount", mergedCandidates.size(),
                    "coverageTypes", coverageTypes
            ));
            return task(id);
        } catch (RuntimeException exception) {
            TestDesignTask failed = withTaskStatus(running, TestDesignTaskStatus.FAILED,
                    TestDesignGenerationService.safeErrorMessage(exception));
            repository.saveTask(failed);
            writeAudit("RETRY", "TEST_DESIGN_TASK", id, task.projectId(), Map.of(
                    "taskId", id,
                    "result", "FAILED",
                    "message", TestDesignGenerationService.safeErrorMessage(exception)
            ));
            return task(id);
        }
    }

    /**
     * Manually re-emits the generation event for a queued task without mutating task state.
     *
     * <p>The actual generation consumer still performs a conditional QUEUED -> RUNNING claim, so repeated manual
     * replays, delayed broker delivery and automated recovery scans remain idempotent and cannot duplicate candidates.
     */
    @Transactional
    public TestDesignTaskDetailResponse replayQueuedTaskEvent(UUID id) {
        if (!properties.generationEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP5 用例生成未启用");
        }
        if (!properties.asyncGenerationEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前未启用异步生成，不支持排队事件重发");
        }
        TestDesignTask task = taskOrThrow(id);
        if (!TestDesignTaskStatus.QUEUED.name().equals(task.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅 QUEUED 任务支持排队事件重发: " + task.status());
        }
        eventPublisher.publishGenerationRequested(id);
        writeAudit("GENERATION_EVENT_REPLAY", "TEST_DESIGN_TASK", id, task.projectId(), Map.of(
                "taskId", id,
                "status", task.status(),
                "manualQueuedEventReplay", true
        ));
        return task(id);
    }

    /**
     * Processes one queued generation task after the create transaction commits.
     *
     * <p>The task is claimed with a conditional status transition so duplicate local/Kafka delivery and recovery
     * replays are harmless. Candidate production is delegated to the generation service after the task is claimed.
     */
    @Transactional
    public TestDesignTaskDetailResponse processQueuedTask(UUID id) {
        return generateQueuedTask(id, TestDesignTaskStatus.QUEUED);
    }

    /**
     * Cancels a generation task at the task state level.
     *
     * <p>Candidate rows are intentionally left untouched: they are review evidence and may be reused if a cancelled or
     * partially failed task is retried later.
     */
    @Transactional
    public TestDesignTaskDetailResponse cancelTask(UUID id) {
        TestDesignTask task = taskOrThrow(id);
        if (TestDesignTaskStatus.CANCELLED.name().equals(task.status())) {
            return task(id);
        }
        if (!CANCELLABLE_TASK_STATUSES.contains(task.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前任务状态不可取消: " + task.status());
        }
        TestDesignTask cancelled = withTaskStatus(task, TestDesignTaskStatus.CANCELLED, "用户取消生成任务");
        repository.saveTask(cancelled);
        writeAudit("CANCEL", "TEST_DESIGN_TASK", id, task.projectId(), Map.of(
                "taskId", id,
                "fromStatus", task.status(),
                "toStatus", TestDesignTaskStatus.CANCELLED.name()
        ));
        return task(id);
    }

    private TestDesignTaskDetailResponse generateQueuedTask(UUID id, TestDesignTaskStatus expectedStatus) {
        Instant startedAt = Instant.now();
        if (!repository.markTaskStatus(id, expectedStatus, TestDesignTaskStatus.RUNNING, startedAt)) {
            log.info("Skip WP5 generation because task is no longer {}, task_id={}", expectedStatus.name(), id);
            return task(id);
        }
        TestDesignTask running = taskOrThrow(id);
        try {
            List<RequirementResponse> requirements = requirementIdsFromText(running.requirementIds()).stream()
                    .map(assetService::getRequirement)
                    .peek(requirement -> ensureSameProject(requirement, running.projectId()))
                    .toList();
            List<String> coverageTypes = normalizedCoverageTypes(csvValues(running.coverageTypes()));
            TestDesignGenerationService.GenerationAttempt attempt =
                    generationService.generateCandidates(running, requirements, coverageTypes, Instant.now());
            attempt.candidates().forEach(repository::saveCandidate);
            TestDesignTask succeeded = withTaskCounts(
                    withTaskStatus(attempt.task(), TestDesignTaskStatus.SUCCEEDED, attempt.warningMessage()),
                    attempt.candidates()
            );
            repository.saveTask(succeeded);
            writeAudit("GENERATE", "TEST_DESIGN_TASK", id, running.projectId(), Map.of(
                    "taskId", id,
                    "candidateCount", attempt.candidates().size(),
                    "coverageTypes", coverageTypes
            ));
            return task(id);
        } catch (RuntimeException exception) {
            TestDesignTask failed = withTaskStatus(running, TestDesignTaskStatus.FAILED,
                    TestDesignGenerationService.safeErrorMessage(exception));
            repository.saveTask(failed);
            writeAudit("GENERATE", "TEST_DESIGN_TASK", id, running.projectId(), Map.of(
                    "taskId", id,
                    "result", "FAILED",
                    "message", TestDesignGenerationService.safeErrorMessage(exception)
            ));
            log.warn("WP5 test design generation failed, task_id={}, message={}",
                    id, TestDesignGenerationService.safeErrorMessage(exception), exception);
            return task(id);
        }
    }

    private static TestDesignTask withTaskCounts(TestDesignTask task, List<TestDesignCandidate> candidates) {
        int generatedCount = candidates.size();
        int confirmedCount = Math.toIntExact(candidates.stream()
                .filter(candidate -> TestDesignCandidateStatus.CONFIRMED.name().equals(candidate.status()))
                .count());
        int publishedCount = Math.toIntExact(candidates.stream()
                .filter(candidate -> TestDesignCandidateStatus.PUBLISHED.name().equals(candidate.status()))
                .count());
        String status = publishedCount > 0 && publishedCount == generatedCount
                ? TestDesignTaskStatus.PUBLISHED.name()
                : task.status();
        return new TestDesignTask(
                task.id(), task.projectId(), task.title(), status, task.requirementIds(), task.coverageTypes(),
                task.promptKey(), task.promptVersion(), task.modelInvocationId(), task.modelProviderName(),
                task.modelName(), task.totalRequirements(), generatedCount, confirmedCount, publishedCount,
                task.errorMessage(), task.requestedBy(), task.idempotencyKey(), task.requestDigest(),
                task.inputDigest(), task.contextSummaryJson(), task.createdAt(), Instant.now()
        );
    }

    private static TestDesignTask withTaskStatus(
            TestDesignTask task,
            TestDesignTaskStatus status,
            String errorMessage
    ) {
        return new TestDesignTask(
                task.id(), task.projectId(), task.title(), status.name(), task.requirementIds(), task.coverageTypes(),
                task.promptKey(), task.promptVersion(), task.modelInvocationId(), task.modelProviderName(),
                task.modelName(), task.totalRequirements(), task.generatedCount(), task.confirmedCount(),
                task.publishedCount(), errorMessage, task.requestedBy(), task.idempotencyKey(), task.requestDigest(),
                task.inputDigest(), task.contextSummaryJson(), task.createdAt(), Instant.now()
        );
    }

    private Map<UUID, TestDesignCandidate> candidateById(List<TestDesignCandidate> candidates) {
        return candidates.stream().collect(Collectors.toMap(TestDesignCandidate::id, Function.identity()));
    }

    private void validateProjectWhenProvided(String projectId) {
        if (StringUtils.hasText(projectId)) {
            contextClient.projectContext(projectId);
        }
    }

    private TestDesignTask taskOrThrow(UUID id) {
        return repository.task(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例生成任务不存在: " + id));
    }

    private static void ensureSameProject(RequirementResponse requirement, String projectId) {
        if (!projectId.equals(requirement.projectId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "需求不属于当前项目: " + requirement.id());
        }
    }

    private static List<UUID> distinctRequirementIds(List<UUID> requirementIds) {
        return distinctIds(requirementIds);
    }

    private static List<UUID> distinctIds(List<UUID> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void validateExplicitContextLimit(
            TestDesignGenerationService.ExplicitContextAssetIds explicitContext,
            TestDesignContextPolicyService.EffectiveContextPolicySnapshot effectivePolicy
    ) {
        int limit = effectivePolicy.contextLimits().getOrDefault(
                "explicitAssetsPerType",
                properties.effectiveContextExplicitAssetsPerType()
        );
        validateExplicitContextLimit("contextApiIds", explicitContext.apiIds().size(), limit);
        validateExplicitContextLimit("contextPageIds", explicitContext.pageIds().size(), limit);
        validateExplicitContextLimit("contextFlowIds", explicitContext.flowIds().size(), limit);
    }

    private static void validateExplicitContextLimit(String fieldName, int size, int limit) {
        if (size > limit) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    fieldName + " 单次最多支持 " + limit + " 个"
            );
        }
    }

    private static List<UUID> requirementIdsFromText(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (String value : rawValue.split(",")) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            try {
                ids.add(UUID.fromString(value.trim()));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.INVALID_STATE, "任务需求 ID 格式非法: " + value.trim());
            }
        }
        return ids.stream().distinct().toList();
    }

    private static List<String> csvValues(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return List.of();
        }
        return List.of(rawValue.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private static String taskTitle(String requestedTitle, List<RequirementResponse> requirements) {
        if (StringUtils.hasText(requestedTitle)) {
            return redactSensitiveText(requestedTitle.trim());
        }
        if (requirements.size() == 1) {
            return "生成 " + redactSensitiveText(requirements.getFirst().title()) + " 测试用例";
        }
        return "批量生成 " + requirements.size() + " 个需求的测试用例";
    }

    private Optional<TestDesignTask> replayIdempotentTaskIfPresent(
            String projectId,
            String idempotencyKey,
            String requestDigest
    ) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return Optional.empty();
        }
        Optional<TestDesignTask> existingTask = repository.taskByIdempotencyKey(projectId, idempotencyKey);
        if (existingTask.isEmpty()) {
            return Optional.empty();
        }
        TestDesignTask task = existingTask.get();
        if (!Objects.equals(task.requestDigest(), requestDigest)) {
            throw new BusinessException(ErrorCode.CONFLICT, "幂等键已被不同创建请求使用，请更换 Idempotency-Key");
        }
        writeAudit("IDEMPOTENT_REPLAY", "TEST_DESIGN_TASK", task.id(), projectId, Map.of(
                "taskId", task.id(),
                "idempotencyKey", idempotencyKey
        ));
        return existingTask;
    }

    private String taskRequestDigest(
            String projectId,
            String requestedTitle,
            List<UUID> requirementIds,
            TestDesignGenerationService.ExplicitContextAssetIds explicitContext,
            String environmentKey,
            TestDesignContextPolicyService.EffectiveContextPolicySnapshot effectivePolicy,
            List<String> coverageTypes,
            int caseCountPerRequirement,
            String promptKey,
            String promptVersion,
            UUID templateId,
            String templateName
    ) {
        // Hash only immutable request inputs and generation config; mutable requirement titles/content are excluded.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId);
        payload.put("title", trimToNull(requestedTitle));
        payload.put("requirementIds", requirementIds.stream().map(UUID::toString).toList());
        payload.put("contextApiIds", explicitContext.apiIds().stream().map(UUID::toString).toList());
        payload.put("contextPageIds", explicitContext.pageIds().stream().map(UUID::toString).toList());
        payload.put("contextFlowIds", explicitContext.flowIds().stream().map(UUID::toString).toList());
        payload.put("environmentKey", trimToNull(environmentKey));
        payload.put("coverageTypes", coverageTypes);
        payload.put("caseCountPerRequirement", caseCountPerRequirement);
        payload.put("promptKey", promptKey);
        payload.put("promptVersion", promptVersion);
        payload.put("templateId", templateId == null ? null : templateId.toString());
        payload.put("templateName", trimToNull(templateName));
        payload.put("generationMode", properties.generationMode());
        payload.put("contextLimits", effectivePolicy.contextLimits());
        payload.put("contextPolicyGovernance", TestDesignContextPolicyGovernance.snapshot(effectivePolicy));
        payload.put("contextPolicyOperations", TestDesignContextPolicyOperations.snapshot(effectivePolicy));
        payload.put("generationOrchestrationPolicy", TestDesignGenerationOrchestrationPolicy.snapshot(properties));
        payload.put("scopePolicy", TestDesignScopePolicy.snapshot());
        payload.put("evaluationCorpusPolicy", TestDesignEvaluationCorpusPolicy.snapshot());
        payload.put("releaseReadinessPolicy", TestDesignReleaseReadinessPolicy.snapshot(properties));
        payload.put("auditChainPolicy", TestDesignAuditChainPolicy.snapshot());
        payload.put("modelObservationPolicy", TestDesignModelObservationPolicy.snapshot());
        payload.put("archivePolicy", TestDesignArchivePolicy.snapshot(properties));
        payload.put("reportManifestPolicy", TestDesignReportManifestPolicy.snapshot());
        try {
            return sha256(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("WP5 task request digest serialization failed", exception);
        }
    }

    private int normalizedCaseCount(Integer requestedCount) {
        return requestedCount == null || requestedCount <= 0 ? maxCasesPerRequirement()
                : Math.min(requestedCount, maxCasesPerRequirement());
    }

    /**
     * Resolves the effective generation config once at task creation time.
     *
     * <p>The resulting values are copied into task columns, request digest and context summary so retries and reports
     * remain stable even if the template is later edited or disabled.</p>
     */
    private EffectiveTaskGenerationConfig effectiveGenerationConfig(
            CreateTestDesignTaskCommand command,
            String projectId
    ) {
        Optional<TestDesignTemplateService.TaskTemplateDefaults> templateDefaults =
                templateService.taskDefaults(command.templateId(), projectId);
        TestDesignTemplateService.TaskTemplateDefaults defaults = templateDefaults.orElse(null);
        String environmentKey = firstText(command.environmentKey(), defaults == null ? null : defaults.environmentKey());
        TestDesignGenerationService.ExplicitContextAssetIds explicitContext =
                new TestDesignGenerationService.ExplicitContextAssetIds(
                        explicitIds(command.contextApiIds(), defaults == null ? List.of() : defaults.contextApiIds()),
                        explicitIds(command.contextPageIds(), defaults == null ? List.of() : defaults.contextPageIds()),
                        explicitIds(command.contextFlowIds(), defaults == null ? List.of() : defaults.contextFlowIds())
                );
        List<String> coverageTypes = normalizedCoverageTypes(
                command.coverageTypes() == null || command.coverageTypes().isEmpty()
                        ? defaults == null ? List.of() : defaults.coverageTypes()
                        : command.coverageTypes()
        );
        Integer requestedCaseCount = command.caseCountPerRequirement();
        int caseCount = requestedCaseCount == null || requestedCaseCount <= 0
                ? defaults == null ? maxCasesPerRequirement() : defaults.caseCountPerRequirement()
                : normalizedCaseCount(requestedCaseCount);
        String promptKey = normalizedPromptRef(firstText(command.promptKey(),
                defaults == null ? properties.promptKey() : defaults.promptKey()), "promptKey", 128);
        String promptVersion = normalizedPromptRef(firstText(command.promptVersion(),
                defaults == null ? properties.promptVersion() : defaults.promptVersion()), "promptVersion", 64);
        return new EffectiveTaskGenerationConfig(
                defaults == null ? null : defaults.templateId(),
                defaults == null ? null : defaults.templateName(),
                environmentKey,
                explicitContext,
                coverageTypes,
                caseCount,
                promptKey,
                promptVersion
        );
    }

    private static List<UUID> explicitIds(List<UUID> requestedIds, List<UUID> templateDefaultIds) {
        List<UUID> requested = distinctIds(requestedIds);
        return requested.isEmpty() ? distinctIds(templateDefaultIds) : requested;
    }

    private static String normalizedPromptRef(String value, String fieldName, int maxLength) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 长度不能超过 " + maxLength);
        }
        if (TestDesignSensitiveText.containsSensitiveText(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能包含疑似敏感信息");
        }
        if (!PROMPT_REF_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 仅支持字母、数字、点、冒号、下划线和连字符");
        }
        return normalized;
    }

    private static String firstText(String requested, String fallback) {
        return StringUtils.hasText(requested) ? requested.trim() : trimToNull(fallback);
    }

    private static Map<String, Object> templateSummary(EffectiveTaskGenerationConfig config) {
        if (config.templateId() == null) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("templateId", config.templateId().toString());
        summary.put("name", config.templateName());
        return summary;
    }

    private TestDesignGenerationOrchestrationPolicy.RuntimeSnapshot orchestrationRuntimeSnapshot(Instant now) {
        long timeoutSeconds = TestDesignGenerationOrchestrationPolicy.runningTimeoutSeconds(properties);
        long staleRunningTaskCount = timeoutSeconds <= 0L
                ? 0L
                : repository.countStaleRunningTasks(now.minusSeconds(timeoutSeconds));
        long oldestQueuedAgeSeconds = repository.oldestTaskUpdatedAtByStatus(TestDesignTaskStatus.QUEUED)
                .map(updatedAt -> TestDesignGenerationOrchestrationPolicy.ageSeconds(now, updatedAt))
                .orElse(0L);
        return new TestDesignGenerationOrchestrationPolicy.RuntimeSnapshot(
                repository.countTasksByStatus(TestDesignTaskStatus.QUEUED),
                repository.countTasksByStatus(TestDesignTaskStatus.RUNNING),
                oldestQueuedAgeSeconds,
                staleRunningTaskCount
        );
    }

    private static String resolveIdempotencyKey(String bodyKey, String headerKey) {
        String normalizedBody = normalizeIdempotencyKey(bodyKey, "idempotencyKey");
        String normalizedHeader = normalizeIdempotencyKey(headerKey, "Idempotency-Key");
        if (StringUtils.hasText(normalizedBody) && StringUtils.hasText(normalizedHeader)
                && !normalizedBody.equals(normalizedHeader)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请求体 idempotencyKey 与 Idempotency-Key 请求头不一致");
        }
        return StringUtils.hasText(normalizedBody) ? normalizedBody : normalizedHeader;
    }

    private static String normalizeIdempotencyKey(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 长度不能超过 " + MAX_IDEMPOTENCY_KEY_LENGTH);
        }
        if (TestDesignSensitiveText.containsSensitiveText(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能包含疑似敏感信息");
        }
        boolean valid = normalized.codePoints().allMatch(TestDesignTaskService::isIdempotencyKeyCharacter);
        if (!valid) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 仅支持字母、数字、点、冒号、下划线和连字符");
        }
        return normalized;
    }

    private static boolean isIdempotencyKeyCharacter(int codePoint) {
        return Character.isLetterOrDigit(codePoint)
                || codePoint == '.'
                || codePoint == ':'
                || codePoint == '_'
                || codePoint == '-';
    }

    private static List<String> normalizedCoverageTypes(List<String> requestedTypes) {
        List<String> source = requestedTypes == null || requestedTypes.isEmpty() ? DEFAULT_COVERAGE_TYPES : requestedTypes;
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String requestedType : source) {
            String normalized = normalizeCoverageType(requestedType, null);
            if (StringUtils.hasText(normalized)) {
                result.add(normalized);
            }
        }
        return result.isEmpty() ? DEFAULT_COVERAGE_TYPES : List.copyOf(result);
    }

    private static String normalizeCoverageType(String rawValue, String fallback) {
        if (!StringUtils.hasText(rawValue)) {
            return fallback;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (!CoverageType.codes().contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的覆盖类型: " + rawValue);
        }
        return normalized;
    }

    private static String idsText(List<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String redactSensitiveText(String value) {
        return TestDesignSensitiveText.redact(value);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private static Map<String, Object> taskAuditDetails(
            UUID taskId,
            int requirementCount,
            int candidateCount,
            List<String> coverageTypes,
            String idempotencyKey,
            String inputDigest,
            TestDesignGenerationService.ExplicitContextAssetIds explicitContext,
            EffectiveTaskGenerationConfig generationConfig
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("taskId", taskId);
        details.put("requirementCount", requirementCount);
        details.put("candidateCount", candidateCount);
        details.put("coverageTypes", coverageTypes);
        details.put("promptKey", generationConfig.promptKey());
        details.put("promptVersion", generationConfig.promptVersion());
        details.put("templateId", generationConfig.templateId() == null ? null : generationConfig.templateId().toString());
        details.put("explicitContextApiCount", explicitContext.apiIds().size());
        details.put("explicitContextPageCount", explicitContext.pageIds().size());
        details.put("explicitContextFlowCount", explicitContext.flowIds().size());
        details.put("idempotencyKeyPresent", StringUtils.hasText(idempotencyKey));
        details.put("inputDigest", inputDigest);
        if (StringUtils.hasText(idempotencyKey)) {
            details.put("idempotencyKey", idempotencyKey);
        }
        return details;
    }

    private int maxRequirementsPerTask() {
        return properties.maxRequirementsPerTask() <= 0 ? 20 : properties.maxRequirementsPerTask();
    }

    private int maxCasesPerRequirement() {
        return properties.maxCasesPerRequirement() <= 0 ? 3 : properties.maxCasesPerRequirement();
    }

    private TestDesignTaskStatus initialTaskStatus() {
        return properties.asyncGenerationEnabled() ? TestDesignTaskStatus.QUEUED : TestDesignTaskStatus.RUNNING;
    }

    private void writeAudit(String action, String resourceType, UUID resourceId, String projectId, Map<String, Object> after) {
        contextClient.writeAuditEvent(action, resourceType, resourceId.toString(), projectId, "SUCCEEDED", after);
    }

    private record EffectiveTaskGenerationConfig(
            UUID templateId,
            String templateName,
            String environmentKey,
            TestDesignGenerationService.ExplicitContextAssetIds explicitContext,
            List<String> coverageTypes,
            int caseCountPerRequirement,
            String promptKey,
            String promptVersion
    ) {
    }
}
