package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.command.CreateLinkRequest;
import com.songhg.veri.agent.asset.application.command.CreateTestCaseRequest;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.command.CreateTestDesignTaskCommand;
import com.songhg.veri.agent.testdesign.application.command.TestDesignCandidateActionCommand;
import com.songhg.veri.agent.testdesign.application.command.TestDesignCandidateBatchActionCommand;
import com.songhg.veri.agent.testdesign.application.command.TestDesignPublishCommand;
import com.songhg.veri.agent.testdesign.application.command.UpdateTestDesignCandidateCommand;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCandidateQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskQuery;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCandidateBatchActionItemResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCandidateBatchActionResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCandidateResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignHealthResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishRecordResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignStepResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskDetailResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.CoverageType;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TestDesignService {

    private static final List<String> DEFAULT_COVERAGE_TYPES = List.of("SMOKE", "FUNCTIONAL", "EXCEPTION");
    private static final Set<String> CANDIDATE_PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final String TEST_CASE_SOURCE_AI_GENERATED = "AI_GENERATED";
    private static final String TEST_CASE_SOURCE_REF_PREFIX = "wp5:";
    private static final String ACTION_RETRY_LINK_EXISTING = "RETRY_LINK_EXISTING";
    private static final String ACTION_DUPLICATE_REVIEW_REQUIRED = "DUPLICATE_REVIEW_REQUIRED";
    private static final String RESULT_CONFLICT = "CONFLICT";
    private static final double HIGH_SIMILAR_TITLE_THRESHOLD = 0.86D;
    private static final double HIGH_SIMILAR_CONTENT_THRESHOLD = 0.90D;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final Set<String> RETRYABLE_TASK_STATUSES = Set.of(
            TestDesignTaskStatus.FAILED.name(),
            TestDesignTaskStatus.PARTIAL_SUCCESS.name(),
            TestDesignTaskStatus.CANCELLED.name()
    );
    private static final Set<String> CANCELLABLE_TASK_STATUSES = Set.of(
            TestDesignTaskStatus.DRAFT.name(),
            TestDesignTaskStatus.RUNNING.name(),
            TestDesignTaskStatus.PARTIAL_SUCCESS.name(),
            TestDesignTaskStatus.FAILED.name()
    );
    private final TestDesignRepository repository;
    private final AssetService assetService;
    private final TestDesignPlatformContextClient contextClient;
    private final TestDesignActorResolver actorResolver;
    private final TestDesignResponseMapper responseMapper;
    private final TestDesignCandidateQualityGate qualityGate;
    private final TestDesignProperties properties;
    private final ObjectMapper objectMapper;

    public TestDesignService(
            TestDesignRepository repository,
            AssetService assetService,
            TestDesignPlatformContextClient contextClient,
            TestDesignActorResolver actorResolver,
            TestDesignResponseMapper responseMapper,
            TestDesignCandidateQualityGate qualityGate,
            TestDesignProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.assetService = assetService;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.responseMapper = responseMapper;
        this.qualityGate = qualityGate;
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
     * Creates a task and uses deterministic templates for the first WP5 slice.
     *
     * <p>The task still persists prompt/model metadata, so switching to a WP2 model-backed generator later only needs
     * to replace candidate production, not the review or publish contract.
     */
    @Transactional
    public TestDesignTaskDetailResponse createTask(CreateTestDesignTaskCommand command) {
        return createTask(command, null);
    }

    /**
     * Creates a task with project-scoped idempotency support.
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
        List<String> coverageTypes = normalizedCoverageTypes(command.coverageTypes());
        String requestDigest = taskRequestDigest(
                projectId,
                command.title(),
                requirementIds,
                coverageTypes,
                command.caseCountPerRequirement()
        );
        Optional<TestDesignTask> replayedTask = replayIdempotentTaskIfPresent(projectId, idempotencyKey, requestDigest);
        if (replayedTask.isPresent()) {
            return task(replayedTask.get().id());
        }
        List<RequirementResponse> requirements = requirementIds.stream()
                .map(assetService::getRequirement)
                .peek(requirement -> ensureSameProject(requirement, projectId))
                .toList();
        TestDesignGenerationContext generationContext = generationContext(projectId, requirements);
        Instant now = Instant.now();
        String title = taskTitle(command.title(), requirements);
        UUID taskId = UUID.randomUUID();
        String requestedBy = actorResolver.currentActor();
        TestDesignTask task = new TestDesignTask(
                taskId,
                projectId,
                title,
                TestDesignTaskStatus.SUCCEEDED.name(),
                idsText(requirementIds),
                String.join(",", coverageTypes),
                properties.promptKey(),
                properties.promptVersion(),
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
        List<TestDesignCandidate> candidates = new ArrayList<>();
        for (RequirementResponse requirement : requirements) {
            candidates.addAll(generateCandidates(task, requirement, coverageTypes, command.caseCountPerRequirement(), now));
        }
        qualityGate.validateGeneratedBatch(candidates);
        candidates.forEach(repository::saveCandidate);
        TestDesignTask stored = withTaskCounts(task, candidates);
        repository.saveTask(stored);
        writeAudit("CREATE", "TEST_DESIGN_TASK", taskId, projectId, taskAuditDetails(
                taskId,
                requirements.size(),
                candidates.size(),
                coverageTypes,
                idempotencyKey,
                generationContext.inputDigest()
        ));
        return task(taskId);
    }

    /**
     * Retries a failed generation task without deleting reviewed candidates.
     *
     * <p>The current WP5 slice is synchronous and template-backed, so retry fills only missing candidate duplicate keys.
     * This keeps the contract compatible with the later WP2 async generator while protecting manual review work from
     * being overwritten.
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
        Instant now = Instant.now();
        List<TestDesignCandidate> createdCandidates = new ArrayList<>();
        for (RequirementResponse requirement : requirements) {
            List<TestDesignCandidate> generatedCandidates = generateCandidates(running, requirement, coverageTypes, null, now);
            qualityGate.validateGeneratedBatch(generatedCandidates);
            for (TestDesignCandidate candidate : generatedCandidates) {
                if (existingDuplicateKeys.add(candidate.duplicateKey())) {
                    repository.saveCandidate(candidate);
                    createdCandidates.add(candidate);
                }
            }
        }

        List<TestDesignCandidate> mergedCandidates = new ArrayList<>(existingCandidates);
        mergedCandidates.addAll(createdCandidates);
        TestDesignTask finished = withTaskCounts(withTaskStatus(running, TestDesignTaskStatus.SUCCEEDED, null), mergedCandidates);
        repository.saveTask(finished);
        writeAudit("RETRY", "TEST_DESIGN_TASK", id, task.projectId(), Map.of(
                "taskId", id,
                "createdCandidateCount", createdCandidates.size(),
                "totalCandidateCount", mergedCandidates.size(),
                "coverageTypes", coverageTypes
        ));
        return task(id);
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

    public PageResponse<TestDesignCandidateResponse> candidates(TestDesignCandidateQuery query) {
        validateProjectWhenProvided(query.projectId());
        List<TestDesignCandidateResponse> items = repository.candidates(query).stream()
                .map(responseMapper::toCandidateResponse)
                .toList();
        return PageResponse.of(items, query.index(), query.size(), repository.countCandidates(query));
    }

    @Transactional
    public TestDesignCandidateResponse updateCandidate(UUID id, UpdateTestDesignCandidateCommand command) {
        TestDesignCandidate existing = candidateOrThrow(id);
        ensureEditable(existing);
        assertVersion(existing, command.version(), true);
        List<TestDesignStepResponse> steps = normalizeSteps(command.steps(), responseMapper.steps(existing.stepsJson()));
        String expectedResult = expectedResultForUpdate(command.expectedResult(), steps, existing.expectedResult());
        Instant now = Instant.now();
        TestDesignCandidate updated = new TestDesignCandidate(
                existing.id(),
                existing.taskId(),
                existing.projectId(),
                existing.requirementId(),
                command.apiId(),
                command.title().trim(),
                trimToNull(command.description()),
                normalizeCoverageType(command.coverageType(), existing.coverageType()),
                normalizePriority(command.priority(), existing.priority()),
                TestDesignCandidateStatus.EDITED.name(),
                trimToNull(command.preconditions()),
                stepsJson(steps),
                expectedResult,
                tagsText(command.tags()),
                duplicateKey(existing.requirementId(), normalizeCoverageType(command.coverageType(), existing.coverageType()), command.title()),
                existing.confidence(),
                existing.promptKey(),
                existing.promptVersion(),
                existing.modelInvocationId(),
                existing.modelProviderName(),
                existing.modelName(),
                existing.assetCaseId(),
                null,
                null,
                null,
                null,
                existing.confirmedBy(),
                existing.confirmedAt(),
                existing.version() + 1,
                existing.createdAt(),
                now
        );
        qualityGate.validateReviewCandidate(updated, repository.candidatesByTask(existing.taskId()));
        repository.saveCandidate(updated);
        saveReviewRecord(existing, updated, "UPDATE", null);
        refreshTaskCounts(updated.taskId());
        return responseMapper.toCandidateResponse(updated);
    }

    @Transactional
    public TestDesignCandidateResponse confirmCandidate(UUID id, TestDesignCandidateActionCommand command) {
        TestDesignCandidate candidate = changeCandidateStatus(
                id,
                command == null ? null : command.version(),
                TestDesignCandidateStatus.CONFIRMED,
                null,
                null,
                command == null ? null : command.comment()
        );
        return responseMapper.toCandidateResponse(candidate);
    }

    @Transactional
    public TestDesignCandidateResponse rejectCandidate(UUID id, TestDesignCandidateActionCommand command) {
        String reason = requiredReason(command, "驳回候选用例必须填写 reason");
        TestDesignCandidate candidate = changeCandidateStatus(
                id,
                command.version(),
                TestDesignCandidateStatus.REJECTED,
                reason,
                null,
                command.comment()
        );
        return responseMapper.toCandidateResponse(candidate);
    }

    @Transactional
    public TestDesignCandidateResponse ignoreCandidate(UUID id, TestDesignCandidateActionCommand command) {
        String reason = requiredReason(command, "忽略候选用例必须填写 reason");
        TestDesignCandidate candidate = changeCandidateStatus(
                id,
                command.version(),
                TestDesignCandidateStatus.IGNORED,
                null,
                reason,
                command.comment()
        );
        return responseMapper.toCandidateResponse(candidate);
    }

    @Transactional
    public TestDesignCandidateBatchActionResponse batchCandidateAction(TestDesignCandidateBatchActionCommand command) {
        List<TestDesignCandidateBatchTarget> targets = batchTargets(command);
        if (targets.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "批量候选操作必须指定 candidateIds 或 candidates");
        }
        if (targets.size() > batchActionLimit()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "批量候选操作最多支持 " + batchActionLimit() + " 项");
        }
        String action = command.action().trim().toUpperCase(Locale.ROOT);
        if (!List.of("CONFIRM", "REJECT", "IGNORE").contains(action)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的候选批量动作: " + command.action());
        }
        if (("REJECT".equals(action) || "IGNORE".equals(action)) && !StringUtils.hasText(command.reason())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "批量驳回或忽略必须填写 reason");
        }
        List<TestDesignCandidateBatchActionItemResponse> items = new ArrayList<>();
        for (TestDesignCandidateBatchTarget target : targets) {
            try {
                TestDesignCandidateActionCommand itemCommand = new TestDesignCandidateActionCommand(
                        target.version(),
                        command.reason(),
                        command.comment()
                );
                TestDesignCandidateResponse candidate = switch (action) {
                    case "CONFIRM" -> confirmCandidate(target.id(), itemCommand);
                    case "REJECT" -> rejectCandidate(target.id(), itemCommand);
                    case "IGNORE" -> ignoreCandidate(target.id(), itemCommand);
                    default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的候选批量动作: " + action);
                };
                items.add(new TestDesignCandidateBatchActionItemResponse(target.id(), "SUCCEEDED", candidate, null, null));
            } catch (BusinessException exception) {
                items.add(new TestDesignCandidateBatchActionItemResponse(
                        target.id(),
                        "FAILED",
                        null,
                        exception.getErrorCode().name(),
                        exception.getMessage()
                ));
            }
        }
        long succeeded = items.stream().filter(item -> "SUCCEEDED".equals(item.result())).count();
        return new TestDesignCandidateBatchActionResponse(
                action,
                items.size(),
                Math.toIntExact(succeeded),
                items.size() - Math.toIntExact(succeeded),
                items
        );
    }

    /**
     * Runs the publish pipeline in preview mode.
     *
     * <p>Dry-run intentionally does not persist publish records or mutate WP3 assets; callers use it to surface the
     * exact candidate set and publish actions before the irreversible asset write.
     */
    @Transactional
    public TestDesignPublishResponse publishDryRun(UUID taskId, TestDesignPublishCommand command) {
        return publish(taskId, command == null ? new TestDesignPublishCommand(null, true)
                : new TestDesignPublishCommand(command.candidateIds(), true));
    }

    /**
     * Publishes confirmed candidates into WP3 through the asset application service.
     *
     * <p>The method avoids direct table writes so WP3 versioning, trace links, audit and validation rules remain the
     * single source of truth. A failed candidate is recorded locally while successful publishes become immutable
     * published candidates.
     */
    @Transactional
    public TestDesignPublishResponse publish(UUID taskId, TestDesignPublishCommand command) {
        TestDesignTask task = taskOrThrow(taskId);
        boolean dryRun = command != null && Boolean.TRUE.equals(command.dryRun());
        List<TestDesignCandidate> selected = selectPublishCandidates(task, command == null ? null : command.candidateIds());
        if (selected.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "没有已确认候选用例可发布");
        }
        if (!dryRun) {
            repository.saveTask(new TestDesignTask(
                    task.id(), task.projectId(), task.title(), TestDesignTaskStatus.PUBLISHING.name(),
                    task.requirementIds(), task.coverageTypes(), task.promptKey(), task.promptVersion(),
                    task.modelInvocationId(), task.modelProviderName(), task.modelName(), task.totalRequirements(),
                    task.generatedCount(), task.confirmedCount(), task.publishedCount(), task.errorMessage(),
                    task.requestedBy(), task.idempotencyKey(), task.requestDigest(), task.inputDigest(),
                    task.contextSummaryJson(), task.createdAt(), Instant.now()
            ));
        }
        List<TestDesignPublishRecord> records = new ArrayList<>();
        List<UUID> createdCaseIds = new ArrayList<>();
        String actor = actorResolver.currentActor();
        for (TestDesignCandidate candidate : selected) {
            TestDesignPublishRecord record = dryRun
                    ? plannedPublishRecord(task, candidate, actor)
                    : publishCandidate(task, candidate, actor);
            records.add(record);
            if (!dryRun
                    && "CREATE".equals(record.action())
                    && "SUCCEEDED".equals(record.result())
                    && record.assetCaseId() != null) {
                createdCaseIds.add(record.assetCaseId());
            }
        }
        if (!dryRun) {
            records.forEach(repository::savePublishRecord);
            refreshTaskCountsAfterPublish(task.id(), task.status());
        }
        List<TestDesignPublishRecordResponse> responses = records.stream()
                .map(record -> responseMapper.toPublishRecordResponse(record, candidateById(selected).get(record.candidateId())))
                .toList();
        return new TestDesignPublishResponse(
                task.id(),
                task.projectId(),
                dryRun,
                records.size(),
                Math.toIntExact(records.stream().filter(record -> "CREATE".equals(record.action())).count()),
                Math.toIntExact(records.stream().filter(record -> "SKIPPED".equals(record.result())).count()),
                Math.toIntExact(records.stream().filter(record -> "FAILED".equals(record.result())).count()),
                createdCaseIds,
                responses
        );
    }

    public PageResponse<TestDesignPublishRecordResponse> publishRecords(UUID taskId) {
        taskOrThrow(taskId);
        Map<UUID, TestDesignCandidate> candidates = candidateById(repository.candidatesByTask(taskId));
        List<TestDesignPublishRecordResponse> records = repository.publishRecords(taskId).stream()
                .map(record -> responseMapper.toPublishRecordResponse(record, candidates.get(record.candidateId())))
                .toList();
        return PageResponse.of(records, 0, Math.max(1, records.size()), records.size());
    }

    public String taskProjectScopeId(UUID id) {
        return taskOrThrow(id).projectId();
    }

    public String candidateProjectScopeId(UUID id) {
        return candidateOrThrow(id).projectId();
    }

    private TestDesignGenerationContext generationContext(
            String projectId,
            List<RequirementResponse> requirements
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("contextVersion", "wp5-context-v1");
        summary.put("projectId", projectId);
        summary.put("promptKey", properties.promptKey());
        summary.put("promptVersion", properties.promptVersion());
        summary.put("generationMode", properties.generationMode());
        summary.put("requirements", requirements.stream()
                .sorted(Comparator.comparing(RequirementResponse::id))
                .map(this::requirementContextSummary)
                .toList());
        summary.put("existingCasesByRequirement", requirements.stream()
                .sorted(Comparator.comparing(RequirementResponse::id))
                .map(requirement -> existingCaseContextSummary(projectId, requirement.id()))
                .toList());
        summary.put("limits", contextSummaryLimits());
        try {
            String contextSummaryJson = objectMapper.writeValueAsString(summary);
            return new TestDesignGenerationContext(sha256(contextSummaryJson), contextSummaryJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("WP5 generation context summary serialization failed", exception);
        }
    }

    /**
     * Builds the model-ready requirement summary from WP3/WP4-facing fields only.
     *
     * <p>The summary is deliberately redacted and truncated before it is persisted; future WP2 prompt assembly can
     * reuse the same digest to explain exactly which source snapshot produced a task without storing raw prompt text.
     */
    private Map<String, Object> requirementContextSummary(RequirementResponse requirement) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", requirement.id().toString());
        item.put("code", redactedPreview(requirement.code(), 80));
        item.put("title", redactedPreview(requirement.title(), 160));
        item.put("priority", requirement.priority());
        item.put("status", requirement.status());
        item.put("source", redactedPreview(requirement.source(), 80));
        item.put("sourceRef", redactedPreview(requirement.sourceRef(), 160));
        item.put("version", requirement.version());
        item.put("descriptionPreview", redactedPreview(requirement.description(), 240));
        item.put("acceptanceCriteriaPreview", redactedPreview(requirement.acceptanceCriteria(), 240));
        item.put("tags", summaryTags(requirement.tags()));
        return item;
    }

    private Map<String, Object> existingCaseContextSummary(String projectId, UUID requirementId) {
        List<TestCaseResponse> cases = assetService.findActiveTestCasesByRequirement(projectId, requirementId).stream()
                .sorted(Comparator.comparing(TestCaseResponse::title, Comparator.nullsLast(String::compareTo))
                        .thenComparing(TestCaseResponse::id))
                .toList();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("requirementId", requirementId.toString());
        item.put("count", cases.size());
        item.put("cases", cases.stream().limit(5).map(this::testCaseContextSummary).toList());
        return item;
    }

    private Map<String, Object> testCaseContextSummary(TestCaseResponse testCase) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", testCase.id().toString());
        item.put("title", redactedPreview(testCase.title(), 160));
        item.put("priority", testCase.priority());
        item.put("status", testCase.status());
        item.put("source", redactedPreview(testCase.source(), 80));
        item.put("sourceRef", redactedPreview(testCase.sourceRef(), 160));
        item.put("stepCount", testCase.steps() == null ? 0 : testCase.steps().size());
        item.put("descriptionPreview", redactedPreview(testCase.description(), 200));
        return item;
    }

    private static Map<String, Object> contextSummaryLimits() {
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("requirementDescriptionChars", 240);
        limits.put("acceptanceCriteriaChars", 240);
        limits.put("existingCasesPerRequirement", 5);
        limits.put("rawPromptStored", false);
        return limits;
    }

    private record TestDesignGenerationContext(String inputDigest, String contextSummaryJson) {
    }

    private List<TestDesignCandidate> generateCandidates(
            TestDesignTask task,
            RequirementResponse requirement,
            List<String> coverageTypes,
            Integer requestedCount,
            Instant now
    ) {
        int limit = requestedCount == null || requestedCount <= 0 ? maxCasesPerRequirement()
                : Math.min(requestedCount, maxCasesPerRequirement());
        List<String> selectedCoverageTypes = coverageTypes.stream().limit(limit).toList();
        List<TestDesignCandidate> candidates = new ArrayList<>();
        for (String coverageType : selectedCoverageTypes) {
            candidates.add(candidateFromRequirement(task, requirement, coverageType, now));
        }
        return candidates;
    }

    private TestDesignCandidate candidateFromRequirement(
            TestDesignTask task,
            RequirementResponse requirement,
            String coverageType,
            Instant now
    ) {
        UUID id = UUID.randomUUID();
        String requirementTitle = redactSensitiveText(requirement.title());
        String title = switch (coverageType) {
            case "SMOKE" -> "验证" + requirementTitle + "核心冒烟流程";
            case "EXCEPTION" -> "验证" + requirementTitle + "异常提示与阻断";
            case "BOUNDARY" -> "验证" + requirementTitle + "边界条件";
            case "PERMISSION" -> "验证" + requirementTitle + "权限控制";
            case "REGRESSION" -> "回归验证" + requirementTitle;
            default -> "验证" + requirementTitle + "主流程";
        };
        String description = "基于 WP3 需求生成的 " + coverageType + " 候选用例。需求编号: " + requirement.code();
        List<TestDesignStepResponse> steps = templateSteps(requirement, coverageType);
        String expectedResult = steps.isEmpty() ? "满足需求验收标准" : steps.getLast().expectedResult();
        return new TestDesignCandidate(
                id,
                task.id(),
                task.projectId(),
                requirement.id(),
                null,
                title,
                description,
                coverageType,
                priorityFor(requirement.priority(), coverageType),
                TestDesignCandidateStatus.GENERATED.name(),
                preconditions(requirement),
                stepsJson(steps),
                expectedResult,
                tagsText(List.of("wp5", "ai-generated", coverageType.toLowerCase(Locale.ROOT))),
                duplicateKey(requirement.id(), coverageType, title),
                confidenceFor(coverageType),
                task.promptKey(),
                task.promptVersion(),
                task.modelInvocationId(),
                task.modelProviderName(),
                task.modelName(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                now,
                now
        );
    }

    private List<TestDesignStepResponse> templateSteps(RequirementResponse requirement, String coverageType) {
        String requirementTitle = redactSensitiveText(requirement.title());
        String criteria = StringUtils.hasText(requirement.acceptanceCriteria())
                ? redactSensitiveText(requirement.acceptanceCriteria())
                : "需求验收标准被满足";
        return switch (coverageType) {
            case "SMOKE" -> List.of(
                    step(0, "准备满足前置条件的基础测试数据", "测试数据可用于执行核心流程"),
                    step(1, "执行需求「" + requirementTitle + "」的核心操作", "核心操作完成且无阻断错误"),
                    step(2, "检查关键结果和页面/接口反馈", criteria)
            );
            case "EXCEPTION" -> List.of(
                    step(0, "准备缺失或非法的输入数据", "系统接受测试输入并进入校验逻辑"),
                    step(1, "执行需求「" + requirementTitle + "」的异常路径", "系统阻断非法操作"),
                    step(2, "检查错误提示、状态和审计记录", "错误提示清晰且未产生脏数据")
            );
            case "BOUNDARY" -> List.of(
                    step(0, "准备最小值、最大值和临界值数据", "边界数据准备完成"),
                    step(1, "分别提交边界值并观察处理结果", "有效边界通过，无效边界被拒绝"),
                    step(2, "核对结果持久化和提示信息", criteria)
            );
            case "PERMISSION" -> List.of(
                    step(0, "准备有权限与无权限两个账号", "账号权限边界清晰"),
                    step(1, "分别访问需求「" + requirementTitle + "」相关功能", "有权限账号可操作，无权限账号被拒绝"),
                    step(2, "核对权限失败响应和审计结果", "权限拒绝返回 403 或业务阻断信息")
            );
            case "REGRESSION" -> List.of(
                    step(0, "准备历史通过版本的关键输入", "回归基线数据可用"),
                    step(1, "执行需求「" + requirementTitle + "」历史主路径", "历史主路径仍然通过"),
                    step(2, "核对关联需求、接口或页面未出现回归", criteria)
            );
            default -> List.of(
                    step(0, "确认需求前置条件和测试数据", "前置条件满足"),
                    step(1, "执行需求「" + requirementTitle + "」主流程", "主流程完成"),
                    step(2, "核对验收标准", criteria)
            );
        };
    }

    private TestDesignCandidate changeCandidateStatus(
            UUID id,
            Long version,
            TestDesignCandidateStatus status,
            String rejectedReason,
            String ignoredReason,
            String comment
    ) {
        TestDesignCandidate existing = candidateOrThrow(id);
        ensureEditable(existing);
        assertVersion(existing, version, true);
        Instant now = Instant.now();
        TestDesignCandidate updated = new TestDesignCandidate(
                existing.id(), existing.taskId(), existing.projectId(), existing.requirementId(), existing.apiId(),
                existing.title(), existing.description(), existing.coverageType(), existing.priority(), status.name(),
                existing.preconditions(), existing.stepsJson(), existing.expectedResult(), existing.tags(),
                existing.duplicateKey(), existing.confidence(), existing.promptKey(), existing.promptVersion(),
                existing.modelInvocationId(), existing.modelProviderName(), existing.modelName(), existing.assetCaseId(),
                trimToNull(comment),
                rejectedReason,
                ignoredReason,
                null,
                status == TestDesignCandidateStatus.CONFIRMED ? actorResolver.currentActor() : existing.confirmedBy(),
                status == TestDesignCandidateStatus.CONFIRMED ? now : existing.confirmedAt(),
                existing.version() + 1,
                existing.createdAt(),
                now
        );
        repository.saveCandidate(updated);
        saveReviewRecord(existing, updated, status.name(), comment);
        refreshTaskCounts(updated.taskId());
        return updated;
    }

    private TestDesignPublishRecord publishCandidate(TestDesignTask task, TestDesignCandidate candidate, String actor) {
        if (candidate.status().equals(TestDesignCandidateStatus.PUBLISHED.name()) && candidate.assetCaseId() != null) {
            return publishRecord(task, candidate, false, "SKIP_PUBLISHED", "SKIPPED", candidate.assetCaseId(), null, actor);
        }
        if (!isPublishableCandidate(candidate)) {
            return publishRecord(task, candidate, false, "SKIP_UNCONFIRMED", "SKIPPED", null, "候选用例未确认", actor);
        }
        Optional<TestCaseResponse> existingTestCase = existingWp5TestCase(candidate);
        if (existingTestCase.isPresent()) {
            TestCaseResponse testCase = existingTestCase.get();
            String action = TestDesignCandidateStatus.FAILED.name().equals(candidate.status())
                    ? ACTION_RETRY_LINK_EXISTING
                    : "LINK_EXISTING";
            try {
                ensureTraceLink(candidate, testCase);
                TestDesignCandidate linked = withPublishedCandidate(candidate, testCase.id(), null);
                repository.saveCandidate(linked);
                return publishRecord(task, linked, false, action, "SUCCEEDED", testCase.id(), null, actor);
            } catch (BusinessException exception) {
                TestDesignCandidate failed = withFailedCandidate(candidate, testCase.id(), exception.getMessage());
                repository.saveCandidate(failed);
                return publishRecord(task, failed, false, action, "FAILED", testCase.id(), exception.getMessage(), actor);
            }
        }
        Optional<TestCaseResponse> duplicateTestCase = highSimilarRequirementTestCase(candidate);
        if (duplicateTestCase.isPresent()) {
            TestCaseResponse testCase = duplicateTestCase.get();
            return publishRecord(
                    task,
                    candidate,
                    false,
                    ACTION_DUPLICATE_REVIEW_REQUIRED,
                    RESULT_CONFLICT,
                    testCase.id(),
                    duplicateReviewMessage(testCase),
                    actor
            );
        }
        try {
            TestCaseResponse testCase = assetService.createTestCase(new CreateTestCaseRequest(
                    candidate.title(),
                    candidate.description(),
                    candidate.requirementId(),
                    candidate.apiId(),
                    candidate.projectId(),
                    "DRAFT",
                    candidate.priority(),
                    mergeTags(candidate.tags(), "wp5"),
                    toAssetSteps(candidate),
                    TEST_CASE_SOURCE_AI_GENERATED,
                    candidateSourceRef(candidate)
            ));
            ensureTraceLink(candidate, testCase);
            TestDesignCandidate updated = withPublishedCandidate(candidate, testCase.id(), null);
            repository.saveCandidate(updated);
            return publishRecord(task, updated, false, "CREATE", "SUCCEEDED", testCase.id(), null, actor);
        } catch (BusinessException exception) {
            UUID partialCaseId = existingWp5TestCase(candidate).map(TestCaseResponse::id).orElse(candidate.assetCaseId());
            TestDesignCandidate failed = withFailedCandidate(candidate, partialCaseId, exception.getMessage());
            repository.saveCandidate(failed);
            return publishRecord(task, failed, false, "CREATE", "FAILED", partialCaseId, exception.getMessage(), actor);
        }
    }

    /**
     * Converts a confirmed candidate into a planned publish result without side effects.
     *
     * <p>This preserves dry-run idempotence: repeated previews never create WP3 cases, trace links, candidate version
     * changes or publish history rows.
     */
    private TestDesignPublishRecord plannedPublishRecord(TestDesignTask task, TestDesignCandidate candidate, String actor) {
        if (candidate.status().equals(TestDesignCandidateStatus.PUBLISHED.name()) && candidate.assetCaseId() != null) {
            return publishRecord(task, candidate, true, "SKIP_PUBLISHED", "SKIPPED", candidate.assetCaseId(), null, actor);
        }
        if (!isPublishableCandidate(candidate)) {
            return publishRecord(task, candidate, true, "SKIP_UNCONFIRMED", "SKIPPED", null, "候选用例未确认", actor);
        }
        Optional<TestCaseResponse> existingTestCase = existingWp5TestCase(candidate);
        if (existingTestCase.isPresent()) {
            return publishRecord(task, candidate, true, "LINK_EXISTING", "PLANNED", existingTestCase.get().id(), null, actor);
        }
        Optional<TestCaseResponse> duplicateTestCase = highSimilarRequirementTestCase(candidate);
        if (duplicateTestCase.isPresent()) {
            TestCaseResponse testCase = duplicateTestCase.get();
            return publishRecord(
                    task,
                    candidate,
                    true,
                    ACTION_DUPLICATE_REVIEW_REQUIRED,
                    RESULT_CONFLICT,
                    testCase.id(),
                    duplicateReviewMessage(testCase),
                    actor
            );
        }
        return publishRecord(task, candidate, true, "CREATE", "PLANNED", null, null, actor);
    }

    private static boolean isPublishableCandidate(TestDesignCandidate candidate) {
        return TestDesignCandidateStatus.CONFIRMED.name().equals(candidate.status())
                || TestDesignCandidateStatus.FAILED.name().equals(candidate.status());
    }

    /**
     * Completes the WP3 trace-link side effect for both first-time publishes and replayed partial publishes.
     *
     * <p>WP5 and WP3 do not share a single transaction boundary. If a previous attempt created the test case and failed
     * before linking it back to the requirement, publish retry must repair the missing link before marking the candidate
     * as published.
     */
    private void ensureTraceLink(TestDesignCandidate candidate, TestCaseResponse testCase) {
        assetService.createLink(new CreateLinkRequest(
                candidate.requirementId(),
                candidate.apiId(),
                null,
                null,
                testCase.id()
        ));
    }

    private Optional<TestCaseResponse> existingWp5TestCase(TestDesignCandidate candidate) {
        return assetService.findTestCaseBySourceRef(
                candidate.projectId(),
                TEST_CASE_SOURCE_AI_GENERATED,
                candidateSourceRef(candidate)
        );
    }

    private Optional<TestCaseResponse> highSimilarRequirementTestCase(TestDesignCandidate candidate) {
        return assetService.findActiveTestCasesByRequirement(candidate.projectId(), candidate.requirementId()).stream()
                .filter(testCase -> !Objects.equals(candidateSourceRef(candidate), testCase.sourceRef()))
                .filter(testCase -> isHighSimilarTestCase(candidate, testCase))
                .findFirst();
    }

    /**
     * Compares normalized titles first, then the title/expected/step body, to catch near-duplicate WP3 cases before
     * WP5 publishes another asset under the same requirement.
     */
    private boolean isHighSimilarTestCase(TestDesignCandidate candidate, TestCaseResponse testCase) {
        String candidateTitle = normalizeSimilarityText(candidate.title());
        String testCaseTitle = normalizeSimilarityText(testCase.title());
        if (candidateTitle.equals(testCaseTitle)) {
            return true;
        }
        if (similarity(candidateTitle, testCaseTitle) >= HIGH_SIMILAR_TITLE_THRESHOLD) {
            return true;
        }
        String candidateBody = normalizeSimilarityText(candidateSimilarityText(candidate));
        String testCaseBody = normalizeSimilarityText(testCaseSimilarityText(testCase));
        return similarity(candidateBody, testCaseBody) >= HIGH_SIMILAR_CONTENT_THRESHOLD;
    }

    private String candidateSimilarityText(TestDesignCandidate candidate) {
        String steps = responseMapper.steps(candidate.stepsJson()).stream()
                .map(step -> step.action() + " " + step.expectedResult())
                .collect(Collectors.joining(" "));
        return String.join(" ", nullToEmpty(candidate.title()), nullToEmpty(candidate.expectedResult()), steps);
    }

    private static String testCaseSimilarityText(TestCaseResponse testCase) {
        String steps = testCase.steps().stream()
                .map(step -> step.action() + " " + step.expectedResult())
                .collect(Collectors.joining(" "));
        return String.join(" ", nullToEmpty(testCase.title()), nullToEmpty(testCase.description()), steps);
    }

    private static String duplicateReviewMessage(TestCaseResponse testCase) {
        return "同一需求下已存在高相似测试用例，需人工确认后再发布: " + testCase.code();
    }

    private static String candidateSourceRef(TestDesignCandidate candidate) {
        return TEST_CASE_SOURCE_REF_PREFIX + candidate.id();
    }

    private TestDesignPublishRecord publishRecord(
            TestDesignTask task,
            TestDesignCandidate candidate,
            boolean dryRun,
            String action,
            String result,
            UUID assetCaseId,
            String errorMessage,
            String actor
    ) {
        return new TestDesignPublishRecord(
                UUID.randomUUID(),
                task.id(),
                candidate.id(),
                candidate.projectId(),
                candidate.requirementId(),
                assetCaseId,
                dryRun,
                action,
                result,
                errorMessage,
                actor,
                Instant.now()
        );
    }

    private TestDesignTask withTaskCounts(TestDesignTask task, List<TestDesignCandidate> candidates) {
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

    private void refreshTaskCounts(UUID taskId) {
        TestDesignTask task = taskOrThrow(taskId);
        repository.saveTask(withTaskCounts(task, repository.candidatesByTask(taskId)));
    }

    /**
     * Publish conflicts or partial publish results must not leave the task stuck in the transient PUBLISHING state.
     */
    private void refreshTaskCountsAfterPublish(UUID taskId, String fallbackStatus) {
        TestDesignTask task = taskOrThrow(taskId);
        TestDesignTask counted = withTaskCounts(task, repository.candidatesByTask(taskId));
        if (!TestDesignTaskStatus.PUBLISHING.name().equals(counted.status())) {
            repository.saveTask(counted);
            return;
        }
        repository.saveTask(new TestDesignTask(
                counted.id(), counted.projectId(), counted.title(), fallbackStatus, counted.requirementIds(),
                counted.coverageTypes(), counted.promptKey(), counted.promptVersion(), counted.modelInvocationId(),
                counted.modelProviderName(), counted.modelName(), counted.totalRequirements(), counted.generatedCount(),
                counted.confirmedCount(), counted.publishedCount(), counted.errorMessage(), counted.requestedBy(),
                counted.idempotencyKey(), counted.requestDigest(), counted.inputDigest(), counted.contextSummaryJson(),
                counted.createdAt(), Instant.now()
        ));
    }

    private void saveReviewRecord(TestDesignCandidate before, TestDesignCandidate after, String action, String comment) {
        repository.saveReviewRecord(new TestDesignReviewRecord(
                UUID.randomUUID(),
                after.id(),
                after.taskId(),
                after.projectId(),
                action,
                before.status(),
                after.status(),
                actorResolver.currentActor(),
                trimToNull(comment),
                reviewDiff(before, after),
                Instant.now()
        ));
    }

    private String reviewDiff(TestDesignCandidate before, TestDesignCandidate after) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "titleChanged", !Objects.equals(before.title(), after.title()),
                    "status", Map.of("before", before.status(), "after", after.status()),
                    "version", Map.of("before", before.version(), "after", after.version())
            ));
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private TestDesignCandidate withPublishedCandidate(TestDesignCandidate candidate, UUID assetCaseId, String errorMessage) {
        return new TestDesignCandidate(
                candidate.id(), candidate.taskId(), candidate.projectId(), candidate.requirementId(), candidate.apiId(),
                candidate.title(), candidate.description(), candidate.coverageType(), candidate.priority(),
                TestDesignCandidateStatus.PUBLISHED.name(), candidate.preconditions(), candidate.stepsJson(),
                candidate.expectedResult(), candidate.tags(), candidate.duplicateKey(), candidate.confidence(),
                candidate.promptKey(), candidate.promptVersion(), candidate.modelInvocationId(),
                candidate.modelProviderName(), candidate.modelName(), assetCaseId, candidate.reviewComment(),
                candidate.rejectedReason(), candidate.ignoredReason(), errorMessage, candidate.confirmedBy(),
                candidate.confirmedAt(), candidate.version() + 1, candidate.createdAt(), Instant.now()
        );
    }

    private TestDesignCandidate withFailedCandidate(TestDesignCandidate candidate, UUID assetCaseId, String errorMessage) {
        return new TestDesignCandidate(
                candidate.id(), candidate.taskId(), candidate.projectId(), candidate.requirementId(), candidate.apiId(),
                candidate.title(), candidate.description(), candidate.coverageType(), candidate.priority(),
                TestDesignCandidateStatus.FAILED.name(), candidate.preconditions(), candidate.stepsJson(),
                candidate.expectedResult(), candidate.tags(), candidate.duplicateKey(), candidate.confidence(),
                candidate.promptKey(), candidate.promptVersion(), candidate.modelInvocationId(),
                candidate.modelProviderName(), candidate.modelName(), assetCaseId, candidate.reviewComment(),
                candidate.rejectedReason(), candidate.ignoredReason(), errorMessage, candidate.confirmedBy(),
                candidate.confirmedAt(), candidate.version() + 1, candidate.createdAt(), Instant.now()
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

    private List<TestDesignCandidate> selectPublishCandidates(TestDesignTask task, List<UUID> candidateIds) {
        List<TestDesignCandidate> candidates = repository.candidatesByTask(task.id());
        if (candidateIds == null || candidateIds.isEmpty()) {
            return candidates.stream()
                    .filter(candidate -> TestDesignCandidateStatus.CONFIRMED.name().equals(candidate.status())
                            || TestDesignCandidateStatus.PUBLISHED.name().equals(candidate.status())
                            || TestDesignCandidateStatus.FAILED.name().equals(candidate.status()))
                    .sorted(Comparator.comparing(TestDesignCandidate::createdAt))
                    .toList();
        }
        Map<UUID, TestDesignCandidate> candidateById = candidateById(candidates);
        return candidateIds.stream()
                .distinct()
                .map(id -> {
                    TestDesignCandidate candidate = candidateById.get(id);
                    if (candidate == null) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "候选用例不属于当前任务: " + id);
                    }
                    return candidate;
                })
                .toList();
    }

    private List<CreateTestCaseRequest.StepDto> toAssetSteps(TestDesignCandidate candidate) {
        return responseMapper.steps(candidate.stepsJson()).stream()
                .map(step -> new CreateTestCaseRequest.StepDto(step.action(), step.expectedResult()))
                .toList();
    }

    private Map<UUID, TestDesignCandidate> candidateById(List<TestDesignCandidate> candidates) {
        return candidates.stream().collect(Collectors.toMap(TestDesignCandidate::id, Function.identity()));
    }

    private record TestDesignCandidateBatchTarget(UUID id, Long version) {
    }

    private List<TestDesignCandidateBatchTarget> batchTargets(TestDesignCandidateBatchActionCommand command) {
        if (command.candidates() != null && !command.candidates().isEmpty()) {
            return command.candidates().stream()
                    .filter(Objects::nonNull)
                    .map(item -> {
                        if (item.id() == null) {
                            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "批量候选项 id 不能为空");
                        }
                        return new TestDesignCandidateBatchTarget(item.id(), item.version());
                    })
                    .toList();
        }
        if (command.candidateIds() == null) {
            return List.of();
        }
        return command.candidateIds().stream()
                .map(id -> new TestDesignCandidateBatchTarget(id, null))
                .toList();
    }

    private List<TestDesignStepResponse> normalizeSteps(
            List<UpdateTestDesignCandidateCommand.StepCommand> commands,
            List<TestDesignStepResponse> fallback
    ) {
        if (commands == null) {
            return fallback;
        }
        List<TestDesignStepResponse> steps = new ArrayList<>();
        for (int index = 0; index < commands.size(); index++) {
            UpdateTestDesignCandidateCommand.StepCommand command = commands.get(index);
            if (command == null) {
                continue;
            }
            steps.add(step(index, trimToNull(command.action()), trimToNull(command.expectedResult())));
        }
        return steps;
    }

    private String stepsJson(List<TestDesignStepResponse> steps) {
        return responseMapper.stepsJson(steps);
    }

    private static TestDesignStepResponse step(int order, String action, String expectedResult) {
        return new TestDesignStepResponse(order, action, expectedResult);
    }

    private String expectedResultForUpdate(
            String requestedValue,
            List<TestDesignStepResponse> steps,
            String fallback
    ) {
        String value = trimToNull(requestedValue);
        if (StringUtils.hasText(value)) {
            return value;
        }
        if (steps != null && !steps.isEmpty() && StringUtils.hasText(steps.getLast().expectedResult())) {
            return steps.getLast().expectedResult();
        }
        return fallback;
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

    private TestDesignCandidate candidateOrThrow(UUID id) {
        return repository.candidate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "候选用例不存在: " + id));
    }

    private void ensureEditable(TestDesignCandidate candidate) {
        if (TestDesignCandidateStatus.PUBLISHED.name().equals(candidate.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "已发布候选用例不可编辑");
        }
    }

    private void assertVersion(TestDesignCandidate candidate, Long version, boolean requireVersion) {
        if (version == null) {
            if (requireVersion) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "候选用例版本号不能为空");
            }
            return;
        }
        if (version != candidate.version()) {
            throw new BusinessException(ErrorCode.CONFLICT, "候选用例版本已变化，请刷新后重试");
        }
    }

    private static void ensureSameProject(RequirementResponse requirement, String projectId) {
        if (!projectId.equals(requirement.projectId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "需求不属于当前项目: " + requirement.id());
        }
    }

    private static List<UUID> distinctRequirementIds(List<UUID> requirementIds) {
        if (requirementIds == null) {
            return List.of();
        }
        return requirementIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
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
            List<String> coverageTypes,
            Integer caseCountPerRequirement
    ) {
        // Hash only immutable request inputs and generation config; mutable requirement titles/content are excluded.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId);
        payload.put("title", trimToNull(requestedTitle));
        payload.put("requirementIds", requirementIds.stream().map(UUID::toString).toList());
        payload.put("coverageTypes", coverageTypes);
        payload.put("caseCountPerRequirement", normalizedCaseCount(caseCountPerRequirement));
        payload.put("promptKey", properties.promptKey());
        payload.put("promptVersion", properties.promptVersion());
        payload.put("generationMode", properties.generationMode());
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
        boolean valid = normalized.codePoints().allMatch(TestDesignService::isIdempotencyKeyCharacter);
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

    private static String normalizePriority(String rawValue, String fallback) {
        if (!StringUtils.hasText(rawValue)) {
            return StringUtils.hasText(fallback) ? fallback : "MEDIUM";
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (!CANDIDATE_PRIORITIES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的优先级: " + rawValue);
        }
        return normalized;
    }

    private static String priorityFor(String requirementPriority, String coverageType) {
        if ("EXCEPTION".equals(coverageType) || "PERMISSION".equals(coverageType)) {
            return "HIGH";
        }
        return normalizePriority(requirementPriority, "MEDIUM");
    }

    private static double confidenceFor(String coverageType) {
        return switch (coverageType) {
            case "SMOKE", "FUNCTIONAL" -> 0.86D;
            case "EXCEPTION" -> 0.82D;
            default -> 0.78D;
        };
    }

    private static String preconditions(RequirementResponse requirement) {
        if (StringUtils.hasText(requirement.acceptanceCriteria())) {
            return "需求验收标准已明确，测试前需准备满足业务上下文的数据";
        }
        return "需求描述已确认，测试数据和账号权限已准备";
    }

    private static String redactSensitiveText(String value) {
        // WP5 must not echo obvious secrets from WP3/WP4 source text while the full WP2 context packer is still pending.
        return TestDesignSensitiveText.redact(value);
    }

    private static String redactedPreview(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = redactSensitiveText(value).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static List<String> summaryTags(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return List.of(value.replace('，', ',').split(",")).stream()
                .map(tag -> redactedPreview(tag, 64))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private static String duplicateKey(UUID requirementId, String coverageType, String title) {
        return requirementId + ":" + coverageType + ":" + (title == null ? "" : title.trim().toLowerCase(Locale.ROOT));
    }

    private static String idsText(List<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    private static String tagsText(List<String> tags) {
        if (tags == null) {
            return null;
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String tag : tags) {
            if (StringUtils.hasText(tag)) {
                result.add(tag.trim());
            }
        }
        return result.isEmpty() ? null : String.join(",", result);
    }

    private static String mergeTags(String existingTags, String requiredTag) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (StringUtils.hasText(existingTags)) {
            for (String tag : existingTags.replace('，', ',').split(",")) {
                if (StringUtils.hasText(tag)) {
                    tags.add(tag.trim());
                }
            }
        }
        if (StringUtils.hasText(requiredTag)) {
            tags.add(requiredTag.trim());
        }
        return String.join(",", tags);
    }

    private static String normalizeSimilarityText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        value.trim().toLowerCase(Locale.ROOT).codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(builder::appendCodePoint);
        return builder.toString();
    }

    private static double similarity(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return 0D;
        }
        if (left.equals(right)) {
            return 1D;
        }
        Set<String> leftGrams = grams(left);
        Set<String> rightGrams = grams(right);
        int intersection = 0;
        for (String gram : leftGrams) {
            if (rightGrams.contains(gram)) {
                intersection++;
            }
        }
        int union = leftGrams.size() + rightGrams.size() - intersection;
        return union == 0 ? 0D : (double) intersection / union;
    }

    private static Set<String> grams(String value) {
        int[] codePoints = value.codePoints().toArray();
        if (codePoints.length <= 1) {
            return Set.of(value);
        }
        LinkedHashSet<String> grams = new LinkedHashSet<>();
        for (int i = 0; i < codePoints.length - 1; i++) {
            grams.add(new String(codePoints, i, 2));
        }
        return grams;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String requiredReason(TestDesignCandidateActionCommand command, String message) {
        if (command == null || !StringUtils.hasText(command.reason())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return command.reason().trim();
    }

    private int maxRequirementsPerTask() {
        return properties.maxRequirementsPerTask() <= 0 ? 20 : properties.maxRequirementsPerTask();
    }

    private int maxCasesPerRequirement() {
        return properties.maxCasesPerRequirement() <= 0 ? 3 : properties.maxCasesPerRequirement();
    }

    private int batchActionLimit() {
        return properties.batchActionLimit() <= 0 ? 100 : properties.batchActionLimit();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
            String inputDigest
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("taskId", taskId);
        details.put("requirementCount", requirementCount);
        details.put("candidateCount", candidateCount);
        details.put("coverageTypes", coverageTypes);
        details.put("idempotencyKeyPresent", StringUtils.hasText(idempotencyKey));
        details.put("inputDigest", inputDigest);
        if (StringUtils.hasText(idempotencyKey)) {
            details.put("idempotencyKey", idempotencyKey);
        }
        return details;
    }

    private void writeAudit(String action, String resourceType, UUID resourceId, String projectId, Map<String, Object> after) {
        contextClient.writeAuditEvent(action, resourceType, resourceId.toString(), projectId, "SUCCEEDED", after);
    }
}
