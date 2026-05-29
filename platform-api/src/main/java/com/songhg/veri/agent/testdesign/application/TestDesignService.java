package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.command.CreateLinkRequest;
import com.songhg.veri.agent.asset.application.command.CreateTestCaseRequest;
import com.songhg.veri.agent.asset.application.query.TraceLinkListRequest;
import com.songhg.veri.agent.asset.application.view.ApiResponseDTO;
import com.songhg.veri.agent.asset.application.view.BusinessFlowResponse;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseResponse;
import com.songhg.veri.agent.asset.application.view.TraceLinkResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.CsvEncoder;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.domain.ChatMessage;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import com.songhg.veri.agent.testdesign.application.command.CreateTestDesignTaskCommand;
import com.songhg.veri.agent.testdesign.application.command.ResolveTestDesignConflictCommand;
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
import com.songhg.veri.agent.testdesign.application.view.TestDesignModelObservationResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishRecordResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityDistributionItemResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityMetricResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessCheckResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualitySummaryResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReviewRecordResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TestDesignService {

    private static final Logger log = LoggerFactory.getLogger(TestDesignService.class);
    private static final List<String> DEFAULT_COVERAGE_TYPES = List.of("SMOKE", "FUNCTIONAL", "EXCEPTION");
    private static final Set<String> CANDIDATE_PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final String GENERATION_MODE_RULE_TEMPLATE = "RULE_TEMPLATE";
    private static final String GENERATION_MODE_MODEL = "MODEL";
    private static final String GENERATION_MODE_MODEL_WITH_FALLBACK = "MODEL_WITH_FALLBACK";
    private static final String WP5_MODEL_SCHEMA_MARKER = "WP5_TEST_DESIGN_GENERATION_V1";
    private static final String MODEL_CALLER_SERVICE = "wp5-test-design";
    private static final String MODEL_CAPABILITY_JSON = "JSON";
    private static final String DEFAULT_MODEL_SENSITIVITY_LEVEL = "INTERNAL";
    private static final String TEST_CASE_SOURCE_AI_GENERATED = "AI_GENERATED";
    private static final String TEST_CASE_SOURCE_REF_PREFIX = "wp5:";
    private static final String ACTION_RETRY_LINK_EXISTING = "RETRY_LINK_EXISTING";
    private static final String ACTION_DUPLICATE_REVIEW_REQUIRED = "DUPLICATE_REVIEW_REQUIRED";
    private static final String ACTION_MANUAL_LINK_EXISTING = "MANUAL_LINK_EXISTING";
    private static final String RESULT_CONFLICT = "CONFLICT";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final int CANDIDATE_EXPORT_LIMIT = 500;
    private static final int CANDIDATE_EXPORT_PAGE_SIZE = 100;
    private static final int REVIEW_RECORD_EXPORT_LIMIT = 500;
    private static final int REVIEW_RECORD_EXPORT_PAGE_SIZE = 100;
    private static final int LINKED_ASSET_CONTEXT_LIMIT = 5;
    private static final String READINESS_PASSED = "PASSED";
    private static final String READINESS_WARNING = "WARNING";
    private static final String READINESS_BLOCKED = "BLOCKED";
    private static final String READINESS_CHECK_FAILED = "FAILED";
    private static final String READINESS_SEVERITY_BLOCKING = "BLOCKING";
    private static final String READINESS_SEVERITY_WARNING = "WARNING";
    private static final String READINESS_UNIT_COUNT = "COUNT";
    private static final String READINESS_UNIT_PERCENT = "PERCENT";
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
    private final TestDesignCandidateQualityGate qualityGate;
    private final ModelInvocationService modelInvocationService;
    private final TestDesignModelOutputParser modelOutputParser;
    private final TestDesignProperties properties;
    private final ObjectMapper objectMapper;

    public TestDesignService(
            TestDesignRepository repository,
            TestDesignEventPublisher eventPublisher,
            AssetService assetService,
            TestDesignPlatformContextClient contextClient,
            TestDesignActorResolver actorResolver,
            TestDesignResponseMapper responseMapper,
            TestDesignCandidateQualityGate qualityGate,
            ModelInvocationService modelInvocationService,
            TestDesignModelOutputParser modelOutputParser,
            TestDesignProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.assetService = assetService;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.responseMapper = responseMapper;
        this.qualityGate = qualityGate;
        this.modelInvocationService = modelInvocationService;
        this.modelOutputParser = modelOutputParser;
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
     * Returns task metadata without loading candidate rows so paginated workbench refreshes stay bounded.
     */
    public TestDesignTaskResponse taskSummary(UUID id) {
        return responseMapper.toTaskResponse(taskOrThrow(id));
    }

    /**
     * Builds a full-task quality summary for operations dashboards and release readiness checks.
     *
     * <p>The response is aggregate-only. Candidate descriptions, steps, expected result text, review comments and raw
     * model payloads stay out of the contract so the dashboard can be exposed to auditors without leaking source text.
     */
    public TestDesignQualitySummaryResponse qualitySummary(UUID id) {
        TestDesignTask task = taskOrThrow(id);
        return qualitySummary(task, repository.candidatesByTask(task.id()), Instant.now());
    }

    /**
     * Creates a task and queues deterministic generation for the current WP5 slice.
     *
     * <p>The task still persists prompt/model metadata, so switching the event consumer to a WP2 model-backed
     * generator later only needs to replace candidate production, not the review or publish contract.
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
        List<String> generationCoverageTypes = coverageTypes.stream()
                .limit(normalizedCaseCount(command.caseCountPerRequirement()))
                .toList();
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
                initialTaskStatus().name(),
                idsText(requirementIds),
                String.join(",", generationCoverageTypes),
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
        writeAudit("CREATE", "TEST_DESIGN_TASK", taskId, projectId, taskAuditDetails(
                taskId,
                requirements.size(),
                0,
                generationCoverageTypes,
                idempotencyKey,
                generationContext.inputDigest()
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
     * <p>Retry reuses the configured generation backend and fills only missing candidate duplicate keys. This keeps
     * replay safe for async/model-backed generation while protecting manual review work from being overwritten.
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
            GenerationAttempt attempt = generateCandidatesUsingConfiguredMode(running, requirements, coverageTypes, Instant.now());
            List<TestDesignCandidate> createdCandidates = new ArrayList<>();
            qualityGate.validateGeneratedBatch(attempt.candidates());
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
            TestDesignTask failed = withTaskStatus(running, TestDesignTaskStatus.FAILED, safeErrorMessage(exception));
            repository.saveTask(failed);
            writeAudit("RETRY", "TEST_DESIGN_TASK", id, task.projectId(), Map.of(
                    "taskId", id,
                    "result", "FAILED",
                    "message", safeErrorMessage(exception)
            ));
            return task(id);
        }
    }

    /**
     * Processes one queued generation task after the create transaction commits.
     *
     * <p>The task is claimed with a conditional status transition so duplicate local/Kafka delivery and recovery
     * replays are harmless. Generation still uses deterministic templates in this slice; the event boundary is the
     * future WP2 model invocation handoff.
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

    public PageResponse<TestDesignCandidateResponse> candidates(TestDesignCandidateQuery query) {
        validateProjectWhenProvided(query.projectId());
        List<TestDesignCandidateResponse> items = repository.candidates(query).stream()
                .map(responseMapper::toCandidateResponse)
                .toList();
        return PageResponse.of(items, query.index(), query.size(), repository.countCandidates(query));
    }

    /**
     * Exports a bounded, project-scoped candidate summary for reviewer handoff.
     *
     * <p>The CSV intentionally uses a whitelist of operational fields. Free-form descriptions, preconditions, step
     * bodies, expected-result text, prompt payloads and model input context stay out of the export because they may
     * contain source-document secrets or unreleased product details.
     */
    @Transactional
    public String exportCandidatesCsv(TestDesignCandidateQuery query) {
        CandidateExportScope scope = candidateExportScope(query);
        TestDesignCandidateQuery normalizedQuery = candidateExportQuery(query, scope, 0);
        long totalMatched = repository.countCandidates(normalizedQuery);
        List<TestDesignCandidate> exportedCandidates = new ArrayList<>();
        int pageIndex = 0;
        while (exportedCandidates.size() < CANDIDATE_EXPORT_LIMIT) {
            TestDesignCandidateQuery pageQuery = candidateExportQuery(query, scope, pageIndex);
            List<TestDesignCandidate> pageCandidates = repository.candidates(pageQuery);
            if (pageCandidates.isEmpty()) {
                break;
            }
            int remaining = CANDIDATE_EXPORT_LIMIT - exportedCandidates.size();
            exportedCandidates.addAll(pageCandidates.stream().limit(remaining).toList());
            if (pageCandidates.size() < CANDIDATE_EXPORT_PAGE_SIZE) {
                break;
            }
            pageIndex++;
        }

        StringBuilder csv = new StringBuilder();
        appendCandidateExportHeader(csv);
        appendCandidateExportSummary(csv, "exportLimit", CANDIDATE_EXPORT_LIMIT, scope);
        appendCandidateExportSummary(csv, "totalMatched", totalMatched, scope);
        appendCandidateExportSummary(csv, "exportedCount", exportedCandidates.size(), scope);
        appendCandidateExportSummary(csv, "truncated", totalMatched > exportedCandidates.size(), scope);
        appendCandidateExportSummary(csv, "filters", candidateExportFilterSummary(query), scope);
        appendCandidateExportSummary(csv, "statusCounts", candidateExportCounts(exportedCandidates, TestDesignCandidate::status), scope);
        appendCandidateExportSummary(csv, "coverageCounts", candidateExportCounts(exportedCandidates, TestDesignCandidate::coverageType), scope);
        exportedCandidates.forEach(candidate -> appendCandidateExportRow(csv, candidate));

        writeAudit("EXPORT", "TEST_DESIGN_CANDIDATE", UUID.randomUUID(), scope.projectId(),
                candidateExportAuditDetails(scope, totalMatched, exportedCandidates.size()));
        return csv.toString();
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

    /**
     * Resolves a publish conflict by explicitly linking one reviewed candidate to an existing WP3 test case.
     *
     * <p>The action is intentionally separate from normal publish. High-similarity conflicts stay blocked until a
     * reviewer with publish permission selects the target case, and the service still validates project scope,
     * requirement traceability and candidate version before mutating WP5 state.
     */
    @Transactional
    public TestDesignPublishRecordResponse resolveConflict(UUID candidateId, ResolveTestDesignConflictCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "冲突处理请求不能为空");
        }
        TestDesignCandidate candidate = candidateOrThrow(candidateId);
        TestDesignTask task = taskOrThrow(candidate.taskId());
        assertVersion(candidate, command.version(), true);
        if (!isPublishableCandidate(candidate)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "只有已确认或发布失败的候选用例可处理发布冲突");
        }
        TestCaseResponse testCase = assetService.getTestCase(command.caseId());
        ensureResolvableConflictTarget(candidate, testCase);
        try {
            ensureTraceLink(candidate, testCase);
            TestDesignCandidate linked = withPublishedCandidate(candidate, testCase.id(), null);
            repository.saveCandidate(linked);
            String resolutionComment = manualConflictResolutionMessage(command);
            saveReviewRecord(candidate, linked, "RESOLVE_CONFLICT", resolutionComment);
            TestDesignPublishRecord record = publishRecord(
                    task,
                    linked,
                    false,
                    ACTION_MANUAL_LINK_EXISTING,
                    "SUCCEEDED",
                    testCase.id(),
                    null,
                    actorResolver.currentActor()
            );
            repository.savePublishRecord(record);
            refreshTaskCountsAfterPublish(task.id(), task.status());
            return responseMapper.toPublishRecordResponse(record, linked);
        } catch (BusinessException exception) {
            TestDesignCandidate failed = withFailedCandidate(candidate, testCase.id(), exception.getMessage());
            repository.saveCandidate(failed);
            TestDesignPublishRecord record = publishRecord(
                    task,
                    failed,
                    false,
                    ACTION_MANUAL_LINK_EXISTING,
                    "FAILED",
                    testCase.id(),
                    exception.getMessage(),
                    actorResolver.currentActor()
            );
            repository.savePublishRecord(record);
            refreshTaskCountsAfterPublish(task.id(), task.status());
            return responseMapper.toPublishRecordResponse(record, failed);
        }
    }

    /**
     * Returns a paginated review trail for a task without exposing raw diff JSON or full free-form comments.
     */
    public PageResponse<TestDesignReviewRecordResponse> reviewRecords(UUID taskId, PageQuery pageQuery) {
        TestDesignTask task = taskOrThrow(taskId);
        Map<UUID, TestDesignCandidate> candidates = candidateById(repository.candidatesByTask(taskId));
        List<TestDesignReviewRecordResponse> records = repository.reviewRecords(task.id(), pageQuery).stream()
                .map(record -> toReviewRecordResponse(record, candidates.get(record.candidateId())))
                .toList();
        return PageResponse.of(records, pageQuery.index(), pageQuery.size(), repository.countReviewRecords(task.id()));
    }

    /**
     * Exports a bounded review history CSV for audit handoff.
     *
     * <p>The export keeps only operational metadata and a field-level diff summary. It deliberately excludes raw
     * comments, candidate descriptions, steps, expected results and diff JSON values because reviewers may paste source
     * document details or secrets into those fields.
     */
    @Transactional
    public String exportReviewRecordsCsv(UUID taskId) {
        TestDesignTask task = taskOrThrow(taskId);
        long totalMatched = repository.countReviewRecords(task.id());
        List<TestDesignReviewRecord> exportedRecords = new ArrayList<>();
        int pageIndex = 0;
        while (exportedRecords.size() < REVIEW_RECORD_EXPORT_LIMIT) {
            List<TestDesignReviewRecord> pageRecords = repository.reviewRecords(
                    task.id(),
                    PageQuery.of(pageIndex, REVIEW_RECORD_EXPORT_PAGE_SIZE)
            );
            if (pageRecords.isEmpty()) {
                break;
            }
            int remaining = REVIEW_RECORD_EXPORT_LIMIT - exportedRecords.size();
            exportedRecords.addAll(pageRecords.stream().limit(remaining).toList());
            if (pageRecords.size() < REVIEW_RECORD_EXPORT_PAGE_SIZE) {
                break;
            }
            pageIndex++;
        }

        Map<UUID, TestDesignCandidate> candidates = candidateById(repository.candidatesByTask(task.id()));
        StringBuilder csv = new StringBuilder();
        appendReviewRecordExportHeader(csv);
        appendReviewRecordExportSummary(csv, "exportLimit", REVIEW_RECORD_EXPORT_LIMIT, task);
        appendReviewRecordExportSummary(csv, "totalMatched", totalMatched, task);
        appendReviewRecordExportSummary(csv, "exportedCount", exportedRecords.size(), task);
        appendReviewRecordExportSummary(csv, "truncated", totalMatched > exportedRecords.size(), task);
        appendReviewRecordExportSummary(csv, "actionCounts", reviewRecordActionCounts(exportedRecords), task);
        exportedRecords.forEach(record -> appendReviewRecordExportRow(csv, record, candidates.get(record.candidateId())));

        writeAudit("EXPORT", "TEST_DESIGN_REVIEW_RECORD", UUID.randomUUID(), task.projectId(), Map.of(
                "taskId", task.id(),
                "projectId", task.projectId(),
                "totalMatched", totalMatched,
                "exportedCount", exportedRecords.size(),
                "limit", REVIEW_RECORD_EXPORT_LIMIT,
                "truncated", totalMatched > exportedRecords.size()
        ));
        return csv.toString();
    }

    /**
     * Exports a task-level report using full task scope instead of the frontend's currently loaded pages.
     *
     * <p>The report is intentionally aggregate-only: it contains task metadata, model observation counters, candidate
     * quality counts, review counts and publish counts, but excludes candidate descriptions, step bodies, review
     * comments, trace/job identifiers and raw prompt or model payloads.
     */
    @Transactional
    public String exportTaskReportCsv(UUID taskId) {
        TestDesignTask task = taskOrThrow(taskId);
        TestDesignTaskResponse taskResponse = responseMapper.toTaskResponse(task);
        List<TestDesignCandidate> candidates = repository.candidatesByTask(task.id());
        List<TestDesignReviewRecord> reviewRecords = repository.reviewRecordsByTask(task.id());
        List<TestDesignPublishRecord> publishRecords = repository.publishRecords(task.id());
        Instant generatedAt = Instant.now();

        StringBuilder csv = new StringBuilder();
        appendTaskReportHeader(csv);
        appendTaskReportTaskRows(csv, taskResponse, generatedAt);
        appendTaskReportModelObservationRows(csv, taskResponse, generatedAt);
        appendTaskReportCandidateRows(csv, taskResponse, candidates, generatedAt);
        appendTaskReportReviewRows(csv, taskResponse, reviewRecords, generatedAt);
        appendTaskReportPublishRows(csv, taskResponse, publishRecords, generatedAt);

        writeAudit("EXPORT", "TEST_DESIGN_TASK_REPORT", UUID.randomUUID(), task.projectId(), Map.of(
                "taskId", task.id(),
                "projectId", task.projectId(),
                "candidateCount", candidates.size(),
                "reviewRecordCount", reviewRecords.size(),
                "publishRecordCount", publishRecords.size()
        ));
        return csv.toString();
    }

    public String taskProjectScopeId(UUID id) {
        return taskOrThrow(id).projectId();
    }

    public String candidateProjectScopeId(UUID id) {
        return candidateOrThrow(id).projectId();
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
            GenerationAttempt attempt = generateCandidatesUsingConfiguredMode(running, requirements, coverageTypes, Instant.now());
            qualityGate.validateGeneratedBatch(attempt.candidates());
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
            TestDesignTask failed = withTaskStatus(running, TestDesignTaskStatus.FAILED, safeErrorMessage(exception));
            repository.saveTask(failed);
            writeAudit("GENERATE", "TEST_DESIGN_TASK", id, running.projectId(), Map.of(
                    "taskId", id,
                    "result", "FAILED",
                    "message", safeErrorMessage(exception)
            ));
            log.warn("WP5 test design generation failed, task_id={}, message={}", id, safeErrorMessage(exception), exception);
            return task(id);
        }
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
        summary.put("linkedAssetsByRequirement", requirements.stream()
                .sorted(Comparator.comparing(RequirementResponse::id))
                .map(requirement -> linkedAssetContextSummary(projectId, requirement))
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

    /**
     * Reads linked API/page/flow summaries only through WP3 application services.
     *
     * <p>Trace links may outlive archived or deleted assets, so stale targets are omitted from the model context
     * instead of failing generation for an otherwise valid requirement.
     */
    private Map<String, Object> linkedAssetContextSummary(String projectId, RequirementResponse requirement) {
        List<TraceLinkResponse> links = linksByRequirement(requirement.id());
        List<ApiResponseDTO> apis = links.stream()
                .map(TraceLinkResponse::apiId)
                .filter(Objects::nonNull)
                .distinct()
                .map(apiId -> activeApi(apiId, projectId))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(ApiResponseDTO::code, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ApiResponseDTO::id))
                .toList();
        List<com.songhg.veri.agent.asset.application.view.PageResponse> pages = links.stream()
                .map(TraceLinkResponse::pageId)
                .filter(Objects::nonNull)
                .distinct()
                .map(pageId -> activePage(pageId, projectId))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(
                                com.songhg.veri.agent.asset.application.view.PageResponse::code,
                                Comparator.nullsLast(String::compareTo)
                        )
                        .thenComparing(com.songhg.veri.agent.asset.application.view.PageResponse::id))
                .toList();
        List<BusinessFlowResponse> flows = links.stream()
                .map(TraceLinkResponse::flowId)
                .filter(Objects::nonNull)
                .distinct()
                .map(flowId -> activeBusinessFlow(flowId, projectId))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(BusinessFlowResponse::code, Comparator.nullsLast(String::compareTo))
                        .thenComparing(BusinessFlowResponse::id))
                .toList();

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("requirementId", requirement.id().toString());
        item.put("apiCount", apis.size());
        item.put("pageCount", pages.size());
        item.put("flowCount", flows.size());
        item.put("apis", apis.stream().limit(LINKED_ASSET_CONTEXT_LIMIT).map(this::apiContextSummary).toList());
        item.put("pages", pages.stream().limit(LINKED_ASSET_CONTEXT_LIMIT).map(this::pageContextSummary).toList());
        item.put("flows", flows.stream().limit(LINKED_ASSET_CONTEXT_LIMIT).map(this::businessFlowContextSummary).toList());
        return item;
    }

    private List<TraceLinkResponse> linksByRequirement(UUID requirementId) {
        TraceLinkListRequest request = new TraceLinkListRequest();
        request.setRequirementId(requirementId);
        request.setSize(100);
        return assetService.listLinks(request).items();
    }

    private Optional<ApiResponseDTO> activeApi(UUID apiId, String projectId) {
        try {
            ApiResponseDTO api = assetService.getApi(apiId);
            return sameProject(api.projectId(), projectId) ? Optional.of(api) : Optional.empty();
        } catch (BusinessException exception) {
            if (ErrorCode.NOT_FOUND == exception.getErrorCode()) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private Optional<com.songhg.veri.agent.asset.application.view.PageResponse> activePage(
            UUID pageId,
            String projectId
    ) {
        try {
            com.songhg.veri.agent.asset.application.view.PageResponse page = assetService.getPage(pageId);
            return sameProject(page.projectId(), projectId) ? Optional.of(page) : Optional.empty();
        } catch (BusinessException exception) {
            if (ErrorCode.NOT_FOUND == exception.getErrorCode()) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private Optional<BusinessFlowResponse> activeBusinessFlow(UUID flowId, String projectId) {
        try {
            BusinessFlowResponse flow = assetService.getBusinessFlow(flowId);
            return sameProject(flow.projectId(), projectId) ? Optional.of(flow) : Optional.empty();
        } catch (BusinessException exception) {
            if (ErrorCode.NOT_FOUND == exception.getErrorCode()) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private Map<String, Object> apiContextSummary(ApiResponseDTO api) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", api.id().toString());
        item.put("code", redactedPreview(api.code(), 80));
        item.put("summary", redactedPreview(api.summary(), 160));
        item.put("method", api.httpMethod());
        item.put("path", redactedPreview(api.path(), 160));
        item.put("status", api.status());
        item.put("source", redactedPreview(api.source(), 80));
        item.put("sourceRef", redactedPreview(api.sourceRef(), 160));
        item.put("version", redactedPreview(api.version(), 80));
        item.put("descriptionPreview", redactedPreview(api.description(), 200));
        item.put("requestSchemaPreview", redactedPreview(api.requestSchema(), 240));
        item.put("responseSchemaPreview", redactedPreview(api.responseSchema(), 240));
        return item;
    }

    private Map<String, Object> pageContextSummary(
            com.songhg.veri.agent.asset.application.view.PageResponse page
    ) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", page.id().toString());
        item.put("code", redactedPreview(page.code(), 80));
        item.put("name", redactedPreview(page.name(), 160));
        item.put("urlPattern", redactedPreview(page.urlPattern(), 160));
        item.put("status", page.status());
        item.put("source", redactedPreview(page.source(), 80));
        item.put("sourceRef", redactedPreview(page.sourceRef(), 160));
        item.put("sourceVersion", redactedPreview(page.sourceVersion(), 80));
        item.put("componentTreePreview", redactedPreview(page.componentTree(), 240));
        item.put("screenshotUrl", redactedPreview(page.screenshotUrl(), 160));
        return item;
    }

    private Map<String, Object> businessFlowContextSummary(BusinessFlowResponse flow) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", flow.id().toString());
        item.put("code", redactedPreview(flow.code(), 80));
        item.put("name", redactedPreview(flow.name(), 160));
        item.put("priority", flow.priority());
        item.put("status", flow.status());
        item.put("descriptionPreview", redactedPreview(flow.description(), 200));
        item.put("flowJsonPreview", redactedPreview(flow.flowJson(), 240));
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
        limits.put("linkedAssetsPerRequirement", LINKED_ASSET_CONTEXT_LIMIT);
        limits.put("linkedAssetSchemaChars", 240);
        limits.put("existingCasesPerRequirement", 5);
        limits.put("rawPromptStored", false);
        return limits;
    }

    private record TestDesignGenerationContext(String inputDigest, String contextSummaryJson) {
    }

    /**
     * Selects the generation backend from configuration while keeping the review and publish contracts unchanged.
     *
     * <p>`MODEL` is strict and fails the task when WP2 rejects or returns invalid output. `MODEL_WITH_FALLBACK` records
     * the model failure as a task warning and then uses the deterministic rule template so reviewers still get
     * auditable candidates without mistaking them for pure model output.
     */
    private GenerationAttempt generateCandidatesUsingConfiguredMode(
            TestDesignTask task,
            List<RequirementResponse> requirements,
            List<String> coverageTypes,
            Instant now
    ) {
        String generationMode = normalizedGenerationMode();
        if (GENERATION_MODE_RULE_TEMPLATE.equals(generationMode)) {
            return templateGenerationAttempt(task, requirements, coverageTypes, now, null);
        }
        if (!GENERATION_MODE_MODEL.equals(generationMode) && !GENERATION_MODE_MODEL_WITH_FALLBACK.equals(generationMode)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不支持的 WP5 生成模式: " + properties.generationMode());
        }
        try {
            return modelGenerationAttempt(task, requirements, coverageTypes, now);
        } catch (TestDesignModelGenerationException exception) {
            if (!GENERATION_MODE_MODEL_WITH_FALLBACK.equals(generationMode)) {
                throw exception;
            }
            TestDesignTask taskWithObservation = exception.response() == null
                    ? task
                    : withModelInvocation(task, exception.response());
            return templateGenerationAttempt(taskWithObservation, requirements, coverageTypes, now,
                    modelFallbackWarning(exception));
        } catch (RuntimeException exception) {
            if (!GENERATION_MODE_MODEL_WITH_FALLBACK.equals(generationMode)) {
                throw exception;
            }
            return templateGenerationAttempt(task, requirements, coverageTypes, now, modelFallbackWarning(exception));
        }
    }

    private GenerationAttempt templateGenerationAttempt(
            TestDesignTask task,
            List<RequirementResponse> requirements,
            List<String> coverageTypes,
            Instant now,
            String warningMessage
    ) {
        List<TestDesignCandidate> candidates = new ArrayList<>();
        for (RequirementResponse requirement : requirements) {
            candidates.addAll(generateCandidates(task, requirement, coverageTypes, null, now));
        }
        return new GenerationAttempt(task, candidates, warningMessage);
    }

    private GenerationAttempt modelGenerationAttempt(
            TestDesignTask task,
            List<RequirementResponse> requirements,
            List<String> coverageTypes,
            Instant now
    ) {
        ModelInvocationResult response = null;
        try {
            response = modelInvocationService.invoke(new ModelInvocationCommand(
                    task.projectId(),
                    null,
                    null,
                    task.promptKey(),
                    Map.of("schemaMarker", WP5_MODEL_SCHEMA_MARKER),
                    List.of(new ChatMessage("user", modelGenerationPayload(task, requirements, coverageTypes))),
                    null,
                    null,
                    false,
                    DEFAULT_MODEL_SENSITIVITY_LEVEL,
                    MODEL_CAPABILITY_JSON
            ), new ServicePrincipal(MODEL_CALLER_SERVICE, task.requestedBy()));
            TestDesignTask taskWithInvocation = withModelInvocation(task, response);
            List<TestDesignModelOutputParser.ModelGeneratedCase> generatedCases =
                    modelOutputParser.parse(response.content());
            List<TestDesignCandidate> candidates = candidatesFromModelOutput(
                    taskWithInvocation,
                    requirements,
                    coverageTypes,
                    generatedCases,
                    now
            );
            return new GenerationAttempt(taskWithInvocation, candidates, null);
        } catch (RuntimeException exception) {
            throw new TestDesignModelGenerationException(response, exception);
        }
    }

    private String modelGenerationPayload(
            TestDesignTask task,
            List<RequirementResponse> requirements,
            List<String> coverageTypes
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaMarker", WP5_MODEL_SCHEMA_MARKER);
        payload.put("taskId", task.id().toString());
        payload.put("projectId", task.projectId());
        payload.put("coverageTypes", coverageTypes);
        payload.put("caseCountPerRequirement", Math.min(coverageTypes.size(), maxCasesPerRequirement()));
        payload.put("requirements", requirements.stream().map(this::requirementModelPayload).toList());
        payload.put("contextSummary", contextSummaryPayload(task.contextSummaryJson()));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("WP5 model generation payload serialization failed", exception);
        }
    }

    private Map<String, Object> requirementModelPayload(RequirementResponse requirement) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", requirement.id().toString());
        item.put("code", redactedPreview(requirement.code(), 80));
        item.put("title", redactedPreview(requirement.title(), 160));
        item.put("priority", requirement.priority());
        item.put("description", redactedPreview(requirement.description(), 500));
        item.put("acceptanceCriteria", redactedPreview(requirement.acceptanceCriteria(), 500));
        item.put("tags", summaryTags(requirement.tags()));
        return item;
    }

    private Object contextSummaryPayload(String contextSummaryJson) {
        if (!StringUtils.hasText(contextSummaryJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readTree(contextSummaryJson);
        } catch (JsonProcessingException exception) {
            return Map.of("unavailable", true);
        }
    }

    private List<TestDesignCandidate> candidatesFromModelOutput(
            TestDesignTask task,
            List<RequirementResponse> requirements,
            List<String> coverageTypes,
            List<TestDesignModelOutputParser.ModelGeneratedCase> generatedCases,
            Instant now
    ) {
        Map<String, RequirementResponse> requirementIndex = requirementReferenceIndex(requirements);
        Map<UUID, Integer> countsByRequirement = new LinkedHashMap<>();
        List<TestDesignCandidate> candidates = new ArrayList<>();
        for (TestDesignModelOutputParser.ModelGeneratedCase generatedCase : generatedCases) {
            if (!coverageTypes.contains(generatedCase.coverageType())) {
                continue;
            }
            RequirementResponse requirement = resolveGeneratedRequirement(generatedCase, requirements, requirementIndex);
            int existingCount = countsByRequirement.getOrDefault(requirement.id(), 0);
            if (existingCount >= maxCasesPerRequirement()) {
                continue;
            }
            candidates.add(candidateFromModelCase(task, requirement, generatedCase, now));
            countsByRequirement.put(requirement.id(), existingCount + 1);
        }
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型输出未匹配当前任务需求和覆盖类型");
        }
        return candidates;
    }

    private Map<String, RequirementResponse> requirementReferenceIndex(List<RequirementResponse> requirements) {
        Map<String, RequirementResponse> index = new LinkedHashMap<>();
        for (RequirementResponse requirement : requirements) {
            putRequirementReference(index, requirement.id().toString(), requirement);
            putRequirementReference(index, requirement.code(), requirement);
            putRequirementReference(index, requirement.title(), requirement);
        }
        return index;
    }

    private static void putRequirementReference(
            Map<String, RequirementResponse> index,
            String reference,
            RequirementResponse requirement
    ) {
        if (StringUtils.hasText(reference)) {
            index.put(reference.trim().toLowerCase(Locale.ROOT), requirement);
        }
    }

    private RequirementResponse resolveGeneratedRequirement(
            TestDesignModelOutputParser.ModelGeneratedCase generatedCase,
            List<RequirementResponse> requirements,
            Map<String, RequirementResponse> requirementIndex
    ) {
        if (StringUtils.hasText(generatedCase.requirementRef())) {
            RequirementResponse matched = requirementIndex.get(generatedCase.requirementRef().trim().toLowerCase(Locale.ROOT));
            if (matched != null) {
                return matched;
            }
        }
        if (requirements.size() == 1) {
            return requirements.getFirst();
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型输出缺少可解析的 requirementRef: " + generatedCase.title());
    }

    private TestDesignCandidate candidateFromModelCase(
            TestDesignTask task,
            RequirementResponse requirement,
            TestDesignModelOutputParser.ModelGeneratedCase generatedCase,
            Instant now
    ) {
        String title = redactSensitiveText(generatedCase.title());
        String coverageType = normalizeCoverageType(generatedCase.coverageType(), null);
        List<TestDesignStepResponse> steps = generatedCase.steps().stream()
                .map(step -> step(step.stepOrder(), redactSensitiveText(step.action()),
                        redactSensitiveText(step.expectedResult())))
                .toList();
        return new TestDesignCandidate(
                UUID.randomUUID(),
                task.id(),
                task.projectId(),
                requirement.id(),
                firstUuid(generatedCase.apiRefs()),
                title,
                modelCaseDescription(generatedCase),
                coverageType,
                normalizePriority(generatedCase.priority(), priorityFor(requirement.priority(), coverageType)),
                TestDesignCandidateStatus.GENERATED.name(),
                redactSensitiveText(generatedCase.preconditions()),
                stepsJson(steps),
                redactSensitiveText(generatedCase.expectedResult()),
                tagsText(modelCaseTags(generatedCase)),
                duplicateKey(requirement.id(), coverageType, title),
                generatedCase.confidence(),
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

    private static UUID firstUuid(List<String> refs) {
        if (refs == null) {
            return null;
        }
        for (String ref : refs) {
            if (!StringUtils.hasText(ref)) {
                continue;
            }
            try {
                return UUID.fromString(ref.trim());
            } catch (IllegalArgumentException ignored) {
                // Model references may be external API codes; WP5 only stores UUID-backed API links in this slice.
            }
        }
        return null;
    }

    private static String modelCaseDescription(TestDesignModelOutputParser.ModelGeneratedCase generatedCase) {
        List<String> parts = new ArrayList<>();
        addDescriptionPart(parts, generatedCase.description());
        addDescriptionPart(parts, generatedCase.rationale() == null ? null : "依据: " + generatedCase.rationale());
        addDescriptionPart(parts, generatedCase.riskNotes() == null ? null : "风险: " + generatedCase.riskNotes());
        String description = String.join("\n", parts);
        if (!StringUtils.hasText(description)) {
            return null;
        }
        String redacted = redactSensitiveText(description);
        return redacted.length() <= 2000 ? redacted : redacted.substring(0, 1997) + "...";
    }

    private static void addDescriptionPart(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value.trim());
        }
    }

    private static List<String> modelCaseTags(TestDesignModelOutputParser.ModelGeneratedCase generatedCase) {
        List<String> tags = new ArrayList<>();
        if (generatedCase.tags() != null) {
            tags.addAll(generatedCase.tags());
        }
        tags.add("wp5");
        tags.add("ai-generated");
        tags.add("model");
        tags.add(generatedCase.coverageType().toLowerCase(Locale.ROOT));
        return tags;
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

    private void ensureResolvableConflictTarget(TestDesignCandidate candidate, TestCaseResponse testCase) {
        if (testCase == null || testCase.id() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在");
        }
        if (!Objects.equals(candidate.projectId(), testCase.projectId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "测试用例不属于候选项目: " + testCase.id());
        }
        boolean linkedToCandidateRequirement = assetService.findActiveTestCasesByRequirement(
                        candidate.projectId(),
                        candidate.requirementId()
                ).stream()
                .anyMatch(existing -> Objects.equals(existing.id(), testCase.id()));
        if (!linkedToCandidateRequirement) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "测试用例未关联候选需求: " + testCase.id());
        }
    }

    /**
     * Compares normalized titles first, then the title/expected/step body, to catch near-duplicate WP3 cases.
     *
     * <p>Exact title matches are always blocked. Fuzzy matches use configurable thresholds so release teams can tune
     * strictness by environment without changing publish code or historical WP3 assets.
     */
    private boolean isHighSimilarTestCase(TestDesignCandidate candidate, TestCaseResponse testCase) {
        String candidateTitle = normalizeSimilarityText(candidate.title());
        String testCaseTitle = normalizeSimilarityText(testCase.title());
        if (candidateTitle.equals(testCaseTitle)) {
            return true;
        }
        if (similarity(candidateTitle, testCaseTitle) >= normalizedSimilarityThreshold(properties.conflictTitleSimilarityThreshold())) {
            return true;
        }
        String candidateBody = normalizeSimilarityText(candidateSimilarityText(candidate));
        String testCaseBody = normalizeSimilarityText(testCaseSimilarityText(testCase));
        return similarity(candidateBody, testCaseBody) >= normalizedSimilarityThreshold(properties.conflictContentSimilarityThreshold());
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

    private static String manualConflictResolutionMessage(ResolveTestDesignConflictCommand command) {
        String reason = trimToNull(command.reason());
        String comment = trimToNull(command.comment());
        if (reason == null && comment == null) {
            return "人工确认链接既有测试用例";
        }
        if (reason == null) {
            return "人工确认链接既有测试用例: " + comment;
        }
        if (comment == null) {
            return "人工确认链接既有测试用例: " + reason;
        }
        return "人工确认链接既有测试用例: " + reason + "；" + comment;
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
                    "changedFields", reviewChangedFields(before, after),
                    "status", Map.of("before", before.status(), "after", after.status()),
                    "version", Map.of("before", before.version(), "after", after.version())
            ));
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private static List<String> reviewChangedFields(TestDesignCandidate before, TestDesignCandidate after) {
        List<String> fields = new ArrayList<>();
        if (!Objects.equals(before.title(), after.title())) {
            fields.add("title");
        }
        if (!Objects.equals(before.apiId(), after.apiId())) {
            fields.add("apiId");
        }
        if (!Objects.equals(before.coverageType(), after.coverageType())) {
            fields.add("coverageType");
        }
        if (!Objects.equals(before.priority(), after.priority())) {
            fields.add("priority");
        }
        if (!Objects.equals(before.description(), after.description())) {
            fields.add("description");
        }
        if (!Objects.equals(before.preconditions(), after.preconditions())) {
            fields.add("preconditions");
        }
        if (!Objects.equals(before.stepsJson(), after.stepsJson())) {
            fields.add("steps");
        }
        if (!Objects.equals(before.expectedResult(), after.expectedResult())) {
            fields.add("expectedResult");
        }
        if (!Objects.equals(before.tags(), after.tags())) {
            fields.add("tags");
        }
        if (!Objects.equals(before.status(), after.status())) {
            fields.add("status");
        }
        if (before.version() != after.version()) {
            fields.add("version");
        }
        return fields;
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

    private static TestDesignTask withModelInvocation(TestDesignTask task, ModelInvocationResult response) {
        return new TestDesignTask(
                task.id(), task.projectId(), task.title(), task.status(), task.requirementIds(), task.coverageTypes(),
                task.promptKey(), task.promptVersion(), response.invocationId(), response.providerName(),
                response.modelName(), task.totalRequirements(), task.generatedCount(), task.confirmedCount(),
                task.publishedCount(), task.errorMessage(), task.requestedBy(), task.idempotencyKey(),
                task.requestDigest(), task.inputDigest(), task.contextSummaryJson(), task.createdAt(), Instant.now()
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

    private record GenerationAttempt(
            TestDesignTask task,
            List<TestDesignCandidate> candidates,
            String warningMessage
    ) {
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

    private CandidateExportScope candidateExportScope(TestDesignCandidateQuery query) {
        if (query == null || (query.taskId() == null && !StringUtils.hasText(query.projectId()))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导出候选必须指定 taskId 或 projectId");
        }
        if (query.taskId() != null) {
            TestDesignTask task = taskOrThrow(query.taskId());
            if (StringUtils.hasText(query.projectId())) {
                String requestedProjectId = contextClient.projectContext(query.projectId()).resourceId();
                if (!Objects.equals(requestedProjectId, task.projectId())) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "taskId 与 projectId 不属于同一项目");
                }
            }
            return new CandidateExportScope(task.id(), task.projectId());
        }
        return new CandidateExportScope(null, contextClient.projectContext(query.projectId()).resourceId());
    }

    private TestDesignCandidateQuery candidateExportQuery(
            TestDesignCandidateQuery query,
            CandidateExportScope scope,
            int pageIndex
    ) {
        return new TestDesignCandidateQuery(
                scope.taskId(),
                scope.projectId(),
                query.requirementId(),
                trimToNull(query.status()),
                trimToNull(query.coverageType()),
                trimToNull(query.keyword()),
                PageQuery.of(pageIndex, CANDIDATE_EXPORT_PAGE_SIZE)
        );
    }

    private static void appendCandidateExportHeader(StringBuilder csv) {
        CsvEncoder.appendLine(csv,
                "recordType",
                "metric",
                "value",
                "taskId",
                "projectId",
                "candidateId",
                "requirementId",
                "apiId",
                "title",
                "coverageType",
                "priority",
                "status",
                "version",
                "tags",
                "stepsCount",
                "hasExpectedResult",
                "hasReviewNote",
                "assetCaseId",
                "qualityFlags",
                "errorMessage",
                "createdAt",
                "updatedAt"
        );
    }

    private static void appendCandidateExportSummary(
            StringBuilder csv,
            String metric,
            Object value,
            CandidateExportScope scope
    ) {
        CsvEncoder.appendLine(csv,
                "summary",
                metric,
                value,
                scope.taskId(),
                scope.projectId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private void appendCandidateExportRow(StringBuilder csv, TestDesignCandidate candidate) {
        CsvEncoder.appendLine(csv,
                "candidate",
                null,
                null,
                candidate.taskId(),
                candidate.projectId(),
                candidate.id(),
                candidate.requirementId(),
                candidate.apiId(),
                candidateExportPreview(candidate.title(), 200),
                candidate.coverageType(),
                candidate.priority(),
                candidate.status(),
                candidate.version(),
                String.join("|", summaryTags(candidate.tags())),
                responseMapper.steps(candidate.stepsJson()).size(),
                StringUtils.hasText(candidate.expectedResult()),
                hasCandidateReviewNote(candidate),
                candidate.assetCaseId(),
                candidateExportQualityFlags(candidate),
                candidateExportPreview(candidate.errorMessage(), 240),
                candidate.createdAt(),
                candidate.updatedAt()
        );
    }

    private static boolean hasCandidateReviewNote(TestDesignCandidate candidate) {
        return StringUtils.hasText(candidate.reviewComment())
                || StringUtils.hasText(candidate.rejectedReason())
                || StringUtils.hasText(candidate.ignoredReason());
    }

    private static String candidateExportQualityFlags(TestDesignCandidate candidate) {
        List<String> flags = new ArrayList<>();
        if (StringUtils.hasText(candidate.errorMessage())) {
            flags.add("ERROR_PRESENT");
        }
        if (StringUtils.hasText(candidate.rejectedReason())) {
            flags.add("REJECTED_REASON_PRESENT");
        }
        if (StringUtils.hasText(candidate.ignoredReason())) {
            flags.add("IGNORED_REASON_PRESENT");
        }
        if (candidate.confidence() > 0D && candidate.confidence() < 0.8D) {
            flags.add("LOW_CONFIDENCE");
        }
        return String.join("|", flags);
    }

    private static String candidateExportCounts(
            List<TestDesignCandidate> candidates,
            Function<TestDesignCandidate, String> classifier
    ) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TestDesignCandidate candidate : candidates) {
            String key = classifier.apply(candidate);
            if (StringUtils.hasText(key)) {
                counts.merge(key, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
    }

    private String candidateExportFilterSummary(TestDesignCandidateQuery query) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("taskId", query.taskId());
        filters.put("projectId", candidateExportPreview(query.projectId(), 120));
        filters.put("requirementId", query.requirementId());
        filters.put("status", candidateExportPreview(query.status(), 80));
        filters.put("coverageType", candidateExportPreview(query.coverageType(), 80));
        filters.put("keyword", candidateExportPreview(query.keyword(), 120));
        return filters.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
    }

    private static String candidateExportPreview(String value, int maxLength) {
        String preview = redactedPreview(value, maxLength);
        if (!StringUtils.hasText(preview)) {
            return preview;
        }
        return preview
                .replaceAll("(?i)raw\\s*prompt|rawPrompt", "[REDACTED]")
                .replaceAll("(?i)prompt\\s*plaintext|promptPlaintext", "[REDACTED]")
                .replaceAll("(?i)model\\s*input|modelInput", "[REDACTED]");
    }

    private static Map<String, Object> candidateExportAuditDetails(
            CandidateExportScope scope,
            long totalMatched,
            int exportedCount
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", scope.taskId() == null ? "PROJECT" : "TASK");
        if (scope.taskId() != null) {
            details.put("taskId", scope.taskId());
        }
        details.put("projectId", scope.projectId());
        details.put("totalMatched", totalMatched);
        details.put("exportedCount", exportedCount);
        details.put("limit", CANDIDATE_EXPORT_LIMIT);
        details.put("truncated", totalMatched > exportedCount);
        return details;
    }

    private static void appendTaskReportHeader(StringBuilder csv) {
        CsvEncoder.appendLine(csv,
                "recordType",
                "section",
                "metric",
                "label",
                "value",
                "percent",
                "tone",
                "taskId",
                "taskTitle",
                "taskStatus",
                "projectId",
                "scope",
                "generatedAt",
                "dryRun"
        );
    }

    private static void appendTaskReportTaskRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt
    ) {
        appendTaskReportRow(csv, task, generatedAt, "metadata", "task", "reportType", null,
                "WP5_TASK_REPORT_FULL", null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "task", "generatedAt", null,
                generatedAt, null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "task", "generatedCount", null,
                task.generatedCount(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "task", "confirmedCount", null,
                task.confirmedCount(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "task", "publishedCount", null,
                task.publishedCount(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "task", "totalRequirements", null,
                task.totalRequirements(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "task", "requirementCount", null,
                task.requirementIds().size(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "task", "coverageTypeCount", null,
                task.coverageTypes().size(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "task", "promptKey", null,
                task.promptKey(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "task", "promptVersion", null,
                task.promptVersion(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "task", "modelProviderName", null,
                task.modelProviderName(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "task", "modelName", null,
                task.modelName(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "task", "modelInvocationTracked", null,
                task.modelInvocationId() != null, null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "task", "inputDigestTracked", null,
                StringUtils.hasText(task.inputDigest()), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "task", "contextSummaryKeyCount", null,
                task.contextSummary().size(), null, null, "fullTask", null);
    }

    private static void appendTaskReportModelObservationRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt
    ) {
        TestDesignModelObservationResponse observation = task.modelObservation();
        appendTaskReportRow(csv, task, generatedAt, "metadata", "modelObservation", "available", null,
                observation != null && Boolean.TRUE.equals(observation.available()), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "modelObservation", "traceIdTracked", null,
                observation != null && StringUtils.hasText(observation.traceId()), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "modelObservation", "jobIdTracked", null,
                observation != null && observation.jobId() != null, null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "modelObservation", "status", null,
                observation == null ? null : observation.status(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "modelObservation", "providerName", null,
                observation == null ? null : observation.providerName(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "modelObservation", "modelName", null,
                observation == null ? null : observation.modelName(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "modelObservation", "routingRuleName", null,
                observation == null ? null : observation.routingRuleName(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "modelObservation", "routingGroup", null,
                observation == null ? null : observation.routingGroup(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "modelObservation", "modelCapability", null,
                observation == null ? null : observation.modelCapability(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "modelObservation", "fallbackUsed", null,
                observation == null ? null : observation.fallbackUsed(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "modelObservation", "inputTokens", null,
                observation == null ? null : observation.inputTokens(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "modelObservation", "outputTokens", null,
                observation == null ? null : observation.outputTokens(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "modelObservation", "totalCost", null,
                observation == null ? null : observation.totalCost(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "modelObservation", "latencyMs", null,
                observation == null ? null : observation.latencyMs(), null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "modelObservation", "errorCode", null,
                observation == null ? null : observation.errorCode(), null, null, "fullTask", null);
    }

    private void appendTaskReportCandidateRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            List<TestDesignCandidate> candidates,
            Instant generatedAt
    ) {
        TestDesignQualitySummaryResponse summary = qualitySummary(task, candidates, generatedAt);
        long total = summary.total();
        long noStepsCount = candidates.stream().filter(candidate -> responseMapper.steps(candidate.stepsJson()).isEmpty()).count();
        long reviewNotePresentCount = candidates.stream().filter(TestDesignService::hasCandidateReviewNote).count();

        appendTaskReportRow(csv, task, generatedAt, "metadata", "candidateQuality", "scope", null,
                "fullTask", null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "candidateQuality", "total", null,
                total, null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "summary", "candidateQuality", "metric", "expectedResultPresent",
                summary.expectedCompleteCount(), percent(summary.expectedCompleteCount(), total), null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "summary", "candidateQuality", "metric", "stepExpectedComplete",
                summary.stepCompleteCount(), percent(summary.stepCompleteCount(), total), null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "summary", "candidateQuality", "metric", "publishable",
                summary.publishableCount(), percent(summary.publishableCount(), total), null, "fullTask", null);
        appendTaskReportWarning(csv, task, generatedAt, "candidateQuality", "missingRequirement",
                summary.missingRequirementCount(), total);
        appendTaskReportWarning(csv, task, generatedAt, "candidateQuality", "missingTitle",
                summary.missingTitleCount(), total);
        appendTaskReportWarning(csv, task, generatedAt, "candidateQuality", "noSteps",
                noStepsCount, total);
        appendTaskReportWarning(csv, task, generatedAt, "candidateQuality", "lowConfidence",
                summary.lowConfidenceCount(), total);
        appendTaskReportWarning(csv, task, generatedAt, "candidateQuality", "errorPresent",
                summary.errorCount(), total);
        appendTaskReportWarning(csv, task, generatedAt, "candidateQuality", "reviewNotePresent",
                reviewNotePresentCount, total);
        appendTaskReportWarning(csv, task, generatedAt, "candidateQuality", "duplicateKeyCollision",
                summary.duplicateKeyCollisionCount(), total);
        appendTaskReportDistributionRows(csv, task, generatedAt, "candidateQuality", "status",
                countsBy(candidates, TestDesignCandidate::status), total);
        appendTaskReportDistributionRows(csv, task, generatedAt, "candidateQuality", "coverageType",
                countsBy(candidates, TestDesignCandidate::coverageType), total);
        appendTaskReportDistributionRows(csv, task, generatedAt, "candidateQuality", "priority",
                countsBy(candidates, TestDesignCandidate::priority), total);
    }

    private void appendTaskReportReviewRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            List<TestDesignReviewRecord> records,
            Instant generatedAt
    ) {
        long total = records.size();
        long commentsPresentCount = records.stream()
                .filter(record -> StringUtils.hasText(record.comment()))
                .count();
        long distinctReviewerCount = records.stream()
                .map(TestDesignReviewRecord::reviewer)
                .filter(StringUtils::hasText)
                .distinct()
                .count();
        Map<String, Long> changedFieldCounts = new LinkedHashMap<>();
        for (TestDesignReviewRecord record : records) {
            reviewDiffSummary(record.diffJson()).changedFields()
                    .forEach(field -> changedFieldCounts.merge(field, 1L, Long::sum));
        }

        appendTaskReportRow(csv, task, generatedAt, "metadata", "reviewHistory", "scope", null,
                "fullTask", null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "reviewHistory", "total", null,
                total, null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "summary", "reviewHistory", "metric", "commentsPresent",
                commentsPresentCount, percent(commentsPresentCount, total), null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "summary", "reviewHistory", "metric", "distinctReviewers",
                distinctReviewerCount, null, null, "fullTask", null);
        appendTaskReportDistributionRows(csv, task, generatedAt, "reviewHistory", "action",
                countsBy(records, TestDesignReviewRecord::action), total);
        appendTaskReportDistributionRows(csv, task, generatedAt, "reviewHistory", "afterStatus",
                countsBy(records, TestDesignReviewRecord::afterStatus), total);
        appendTaskReportDistributionRows(csv, task, generatedAt, "reviewHistory", "changedField",
                changedFieldCounts, total);
    }

    private void appendTaskReportPublishRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            List<TestDesignPublishRecord> records,
            Instant generatedAt
    ) {
        long total = records.size();
        long dryRunCount = records.stream().filter(TestDesignPublishRecord::dryRun).count();
        long formalCount = total - dryRunCount;
        long failedCount = records.stream().filter(record -> "FAILED".equals(record.result())).count();
        long assetCaseLinkedCount = records.stream().filter(record -> record.assetCaseId() != null).count();
        long errorPresentCount = records.stream().filter(record -> StringUtils.hasText(record.errorMessage())).count();

        appendTaskReportRow(csv, task, generatedAt, "metadata", "publish", "scope", null,
                "fullTask", null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "metadata", "publish", "total", null,
                total, null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "summary", "publish", "metric", "dryRun",
                dryRunCount, percent(dryRunCount, total), null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "summary", "publish", "metric", "formal",
                formalCount, percent(formalCount, total), null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "summary", "publish", "metric", "assetCaseLinked",
                assetCaseLinkedCount, percent(assetCaseLinkedCount, total), null, "fullTask", null);
        appendTaskReportWarning(csv, task, generatedAt, "publish", "failed", failedCount, total);
        appendTaskReportWarning(csv, task, generatedAt, "publish", "errorPresent", errorPresentCount, total);
        appendTaskReportDistributionRows(csv, task, generatedAt, "publish", "action",
                countsBy(records, TestDesignPublishRecord::action), total);
        appendTaskReportDistributionRows(csv, task, generatedAt, "publish", "result",
                countsBy(records, TestDesignPublishRecord::result), total);
    }

    private static void appendTaskReportWarning(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            String section,
            String label,
            long count,
            long total
    ) {
        appendTaskReportRow(csv, task, generatedAt, "summary", section, "warning", label, count,
                percent(count, total), count > 0 ? "warning" : "neutral", "fullTask", null);
    }

    private static void appendTaskReportDistributionRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            String section,
            String label,
            Map<String, Long> counts,
            long total
    ) {
        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> appendTaskReportRow(csv, task, generatedAt, "summary", section,
                        "distribution:" + label, entry.getKey(), entry.getValue(), percent(entry.getValue(), total),
                        null, "fullTask", null));
    }

    private static void appendTaskReportRow(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            String recordType,
            String section,
            String metric,
            String label,
            Object value,
            String percent,
            String tone,
            String scope,
            Boolean dryRun
    ) {
        CsvEncoder.appendLine(csv,
                recordType,
                section,
                metric,
                label,
                taskReportValue(value),
                percent,
                tone,
                task.id(),
                candidateExportPreview(task.title(), 200),
                task.status(),
                task.projectId(),
                scope,
                generatedAt,
                dryRun
        );
    }

    private static Object taskReportValue(Object value) {
        return value instanceof String text ? candidateExportPreview(text, 240) : value;
    }

    private static <T> Map<String, Long> countsBy(List<T> items, Function<T, String> classifier) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (T item : items) {
            String key = classifier.apply(item);
            counts.merge(StringUtils.hasText(key) ? key : "UNKNOWN", 1L, Long::sum);
        }
        return counts;
    }

    private static String percent(long value, long total) {
        return total <= 0 ? null : String.format(Locale.ROOT, "%.2f", value * 100D / total);
    }

    private static long duplicateKeyCollisionCount(List<TestDesignCandidate> candidates) {
        Map<String, Long> counts = countsBy(candidates, TestDesignCandidate::duplicateKey);
        return candidates.stream()
                .filter(candidate -> StringUtils.hasText(candidate.duplicateKey()))
                .filter(candidate -> counts.getOrDefault(candidate.duplicateKey(), 0L) > 1L)
                .count();
    }

    private TestDesignQualitySummaryResponse qualitySummary(
            TestDesignTaskResponse task,
            List<TestDesignCandidate> candidates,
            Instant generatedAt
    ) {
        long total = candidates.size();
        long reviewableCount = candidates.stream().filter(TestDesignService::isReviewableCandidate).count();
        long publishableCount = candidates.stream().filter(TestDesignService::isPublishableCandidate).count();
        long failedCount = candidates.stream().filter(candidate -> TestDesignCandidateStatus.FAILED.name().equals(candidate.status())).count();
        long confirmedCount = candidates.stream().filter(candidate -> TestDesignCandidateStatus.CONFIRMED.name().equals(candidate.status())).count();
        long publishedCount = candidates.stream().filter(candidate -> TestDesignCandidateStatus.PUBLISHED.name().equals(candidate.status())).count();
        long stepCompleteCount = candidates.stream().filter(this::hasCompleteSteps).count();
        long expectedCompleteCount = candidates.stream().filter(candidate -> StringUtils.hasText(candidate.expectedResult())).count();
        long lowConfidenceCount = candidates.stream().filter(TestDesignService::isLowConfidence).count();
        long errorCount = candidates.stream().filter(candidate -> StringUtils.hasText(candidate.errorMessage())).count();
        long missingRequirementCount = candidates.stream().filter(candidate -> candidate.requirementId() == null).count();
        long missingTitleCount = candidates.stream().filter(candidate -> !StringUtils.hasText(candidate.title())).count();
        long duplicateKeyCollisionCount = duplicateKeyCollisionCount(candidates);
        TestDesignQualityReadinessResponse readiness = qualityReadiness(
                total,
                stepCompleteCount,
                expectedCompleteCount,
                lowConfidenceCount,
                errorCount,
                missingRequirementCount,
                missingTitleCount,
                duplicateKeyCollisionCount
        );
        Map<String, List<TestDesignQualityDistributionItemResponse>> distributions = new LinkedHashMap<>();
        distributions.put("status", qualityDistribution(countsBy(candidates, TestDesignCandidate::status), total));
        distributions.put("coverageType", qualityDistribution(countsBy(candidates, TestDesignCandidate::coverageType), total));
        distributions.put("priority", qualityDistribution(countsBy(candidates, TestDesignCandidate::priority), total));
        return new TestDesignQualitySummaryResponse(
                task.id(),
                task.projectId(),
                candidateExportPreview(task.title(), 200),
                task.status(),
                "fullTask",
                total,
                reviewableCount,
                publishableCount,
                failedCount,
                confirmedCount,
                publishedCount,
                stepCompleteCount,
                expectedCompleteCount,
                lowConfidenceCount,
                errorCount,
                missingRequirementCount,
                missingTitleCount,
                duplicateKeyCollisionCount,
                readiness,
                List.of(
                        qualityMetric("reviewable", reviewableCount, total),
                        qualityMetric("publishable", publishableCount, total),
                        qualityMetric("stepComplete", stepCompleteCount, total),
                        qualityMetric("expectedComplete", expectedCompleteCount, total),
                        qualityMetric("lowConfidence", lowConfidenceCount, total),
                        qualityMetric("errorPresent", errorCount, total)
                ),
                distributions,
                generatedAt
        );
    }

    private TestDesignQualitySummaryResponse qualitySummary(
            TestDesignTask task,
            List<TestDesignCandidate> candidates,
            Instant generatedAt
    ) {
        return qualitySummary(responseMapper.toTaskResponse(task), candidates, generatedAt);
    }

    private boolean hasCompleteSteps(TestDesignCandidate candidate) {
        List<TestDesignStepResponse> steps = responseMapper.steps(candidate.stepsJson());
        return !steps.isEmpty()
                && steps.stream().allMatch(step -> StringUtils.hasText(step.action())
                        && StringUtils.hasText(step.expectedResult()));
    }

    private static TestDesignQualityMetricResponse qualityMetric(String code, long count, long total) {
        return new TestDesignQualityMetricResponse(code, count, percentValue(count, total));
    }

    private static List<TestDesignQualityDistributionItemResponse> qualityDistribution(Map<String, Long> counts, long total) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new TestDesignQualityDistributionItemResponse(
                        entry.getKey(),
                        entry.getValue(),
                        percentValue(entry.getValue(), total)
                ))
                .toList();
    }

    private static double percentValue(long value, long total) {
        return total <= 0 ? 0D : Math.round(value * 10_000D / total) / 100D;
    }

    /**
     * Evaluates configurable WP5 quality readiness thresholds against aggregate counters only.
     *
     * <p>The result is intentionally advisory for the current slice: it gives operations and release reviewers a stable
     * pass/warn/block signal without changing publish authorization or candidate state transitions.
     */
    private TestDesignQualityReadinessResponse qualityReadiness(
            long total,
            long stepCompleteCount,
            long expectedCompleteCount,
            long lowConfidenceCount,
            long errorCount,
            long missingRequirementCount,
            long missingTitleCount,
            long duplicateKeyCollisionCount
    ) {
        double stepCompletePercent = percentValue(stepCompleteCount, total);
        double expectedCompletePercent = percentValue(expectedCompleteCount, total);
        double lowConfidencePercent = percentValue(lowConfidenceCount, total);
        double errorPercent = percentValue(errorCount, total);
        List<TestDesignQualityReadinessCheckResponse> checks = List.of(
                minPercentReadinessCheck("stepComplete", "步骤完整率", stepCompletePercent,
                        properties.readinessMinStepCompletePercent(), READINESS_SEVERITY_BLOCKING,
                        "步骤动作和步骤预期均完整的候选占比不得低于阈值"),
                minPercentReadinessCheck("expectedComplete", "最终预期完整率", expectedCompletePercent,
                        properties.readinessMinExpectedCompletePercent(), READINESS_SEVERITY_BLOCKING,
                        "候选最终预期结果完整占比不得低于阈值"),
                maxPercentReadinessCheck("lowConfidence", "低置信度占比", lowConfidencePercent,
                        properties.readinessMaxLowConfidencePercent(), READINESS_SEVERITY_WARNING,
                        "低置信度候选占比不得高于阈值"),
                maxPercentReadinessCheck("errorPresent", "错误候选占比", errorPercent,
                        properties.readinessMaxErrorPercent(), READINESS_SEVERITY_BLOCKING,
                        "带错误摘要的候选占比不得高于阈值"),
                maxCountReadinessCheck("duplicateKeyCollision", "重复键碰撞", duplicateKeyCollisionCount,
                        properties.readinessMaxDuplicateKeyCollisions(), READINESS_SEVERITY_BLOCKING,
                        "重复键碰撞候选数量不得高于阈值"),
                maxCountReadinessCheck("missingRequirement", "缺少需求关联", missingRequirementCount,
                        properties.readinessMaxMissingRequirementCount(), READINESS_SEVERITY_WARNING,
                        "缺少需求关联的候选数量不得高于阈值"),
                maxCountReadinessCheck("missingTitle", "缺少标题", missingTitleCount,
                        properties.readinessMaxMissingTitleCount(), READINESS_SEVERITY_BLOCKING,
                        "标题缺失候选数量不得高于阈值")
        );
        long blockingCount = failedReadinessCount(checks, READINESS_SEVERITY_BLOCKING);
        long warningCount = failedReadinessCount(checks, READINESS_SEVERITY_WARNING);
        String status = blockingCount > 0 ? READINESS_BLOCKED : warningCount > 0 ? READINESS_WARNING : READINESS_PASSED;
        return new TestDesignQualityReadinessResponse(status, blockingCount, warningCount, checks);
    }

    private static TestDesignQualityReadinessCheckResponse minPercentReadinessCheck(
            String code,
            String label,
            double currentValue,
            double thresholdValue,
            String severity,
            String description
    ) {
        return readinessCheck(code, label, currentValue, thresholdValue, READINESS_UNIT_PERCENT,
                currentValue >= thresholdValue, severity, description);
    }

    private static TestDesignQualityReadinessCheckResponse maxPercentReadinessCheck(
            String code,
            String label,
            double currentValue,
            double thresholdValue,
            String severity,
            String description
    ) {
        return readinessCheck(code, label, currentValue, thresholdValue, READINESS_UNIT_PERCENT,
                currentValue <= thresholdValue, severity, description);
    }

    private static TestDesignQualityReadinessCheckResponse maxCountReadinessCheck(
            String code,
            String label,
            long currentValue,
            long thresholdValue,
            String severity,
            String description
    ) {
        return readinessCheck(code, label, currentValue, thresholdValue, READINESS_UNIT_COUNT,
                currentValue <= thresholdValue, severity, description);
    }

    private static TestDesignQualityReadinessCheckResponse readinessCheck(
            String code,
            String label,
            double currentValue,
            double thresholdValue,
            String unit,
            boolean passed,
            String severity,
            String description
    ) {
        return new TestDesignQualityReadinessCheckResponse(
                code,
                label,
                passed ? READINESS_PASSED : READINESS_CHECK_FAILED,
                severity,
                currentValue,
                thresholdValue,
                unit,
                description
        );
    }

    private static long failedReadinessCount(List<TestDesignQualityReadinessCheckResponse> checks, String severity) {
        return checks.stream()
                .filter(check -> READINESS_CHECK_FAILED.equals(check.status()))
                .filter(check -> severity.equals(check.severity()))
                .count();
    }

    private static boolean isReviewableCandidate(TestDesignCandidate candidate) {
        return TestDesignCandidateStatus.GENERATED.name().equals(candidate.status())
                || TestDesignCandidateStatus.EDITED.name().equals(candidate.status());
    }

    private static boolean isLowConfidence(TestDesignCandidate candidate) {
        return candidate.confidence() > 0D && candidate.confidence() < 0.8D;
    }

    private TestDesignReviewRecordResponse toReviewRecordResponse(
            TestDesignReviewRecord record,
            TestDesignCandidate candidate
    ) {
        ReviewDiffSummary diffSummary = reviewDiffSummary(record.diffJson());
        return new TestDesignReviewRecordResponse(
                record.id(),
                record.taskId(),
                record.candidateId(),
                candidate == null ? null : candidateExportPreview(candidate.title(), 200),
                record.projectId(),
                record.action(),
                record.beforeStatus(),
                record.afterStatus(),
                record.reviewer(),
                StringUtils.hasText(record.comment()),
                candidateExportPreview(record.comment(), 160),
                diffSummary.changedFields(),
                diffSummary.versionBefore(),
                diffSummary.versionAfter(),
                record.createdAt()
        );
    }

    private static void appendReviewRecordExportHeader(StringBuilder csv) {
        CsvEncoder.appendLine(csv,
                "recordType",
                "metric",
                "value",
                "taskId",
                "projectId",
                "reviewRecordId",
                "candidateId",
                "title",
                "action",
                "beforeStatus",
                "afterStatus",
                "reviewer",
                "hasComment",
                "changedFields",
                "versionBefore",
                "versionAfter",
                "createdAt"
        );
    }

    private static void appendReviewRecordExportSummary(
            StringBuilder csv,
            String metric,
            Object value,
            TestDesignTask task
    ) {
        CsvEncoder.appendLine(csv,
                "summary",
                metric,
                value,
                task.id(),
                task.projectId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private void appendReviewRecordExportRow(
            StringBuilder csv,
            TestDesignReviewRecord record,
            TestDesignCandidate candidate
    ) {
        TestDesignReviewRecordResponse response = toReviewRecordResponse(record, candidate);
        CsvEncoder.appendLine(csv,
                "reviewRecord",
                null,
                null,
                record.taskId(),
                record.projectId(),
                record.id(),
                record.candidateId(),
                response.title(),
                record.action(),
                record.beforeStatus(),
                record.afterStatus(),
                record.reviewer(),
                response.hasComment(),
                String.join("|", response.changedFields()),
                response.versionBefore(),
                response.versionAfter(),
                record.createdAt()
        );
    }

    private static String reviewRecordActionCounts(List<TestDesignReviewRecord> records) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TestDesignReviewRecord record : records) {
            if (StringUtils.hasText(record.action())) {
                counts.merge(record.action(), 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
    }

    private ReviewDiffSummary reviewDiffSummary(String diffJson) {
        if (!StringUtils.hasText(diffJson)) {
            return new ReviewDiffSummary(List.of(), null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(diffJson);
            List<String> changedFields = new ArrayList<>();
            JsonNode changedFieldsNode = root.path("changedFields");
            if (changedFieldsNode.isArray()) {
                changedFieldsNode.forEach(field -> {
                    String fieldName = reviewFieldName(field.asText());
                    if (StringUtils.hasText(fieldName)) {
                        changedFields.add(fieldName);
                    }
                });
            }
            if (changedFields.isEmpty() && root.path("titleChanged").asBoolean(false)) {
                changedFields.add("title");
            }
            JsonNode statusNode = root.path("status");
            if (!statusNode.isMissingNode()
                    && !Objects.equals(textOrNull(statusNode.path("before")), textOrNull(statusNode.path("after")))) {
                changedFields.add("status");
            }
            JsonNode versionNode = root.path("version");
            Long versionBefore = longOrNull(versionNode.path("before"));
            Long versionAfter = longOrNull(versionNode.path("after"));
            if (!Objects.equals(versionBefore, versionAfter)) {
                changedFields.add("version");
            }
            return new ReviewDiffSummary(changedFields.stream().distinct().toList(), versionBefore, versionAfter);
        } catch (JsonProcessingException exception) {
            return new ReviewDiffSummary(List.of(), null, null);
        }
    }

    private static String reviewFieldName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replaceAll("[^A-Za-z0-9_.-]", "");
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static Long longOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.canConvertToLong()) {
            return node.longValue();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    private record ReviewDiffSummary(List<String> changedFields, Long versionBefore, Long versionAfter) {
    }

    private record CandidateExportScope(UUID taskId, String projectId) {
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

    private static boolean sameProject(String actualProjectId, String expectedProjectId) {
        return StringUtils.hasText(actualProjectId) && actualProjectId.equals(expectedProjectId);
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

    private static double normalizedSimilarityThreshold(double configuredThreshold) {
        if (!Double.isFinite(configuredThreshold)) {
            return 1D;
        }
        return Math.max(0D, Math.min(1D, configuredThreshold));
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

    private TestDesignTaskStatus initialTaskStatus() {
        return properties.asyncGenerationEnabled() ? TestDesignTaskStatus.QUEUED : TestDesignTaskStatus.RUNNING;
    }

    private String normalizedGenerationMode() {
        if (!StringUtils.hasText(properties.generationMode())) {
            return GENERATION_MODE_RULE_TEMPLATE;
        }
        String normalized = properties.generationMode().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("MODEL_FALLBACK".equals(normalized)) {
            return GENERATION_MODE_MODEL_WITH_FALLBACK;
        }
        if (GENERATION_MODE_MODEL.equals(normalized) && properties.modelFallbackEnabled()) {
            return GENERATION_MODE_MODEL_WITH_FALLBACK;
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String modelFallbackWarning(RuntimeException exception) {
        String message = "模型生成失败，已降级规则模板: " + safeErrorMessage(exception);
        return message.length() <= 500 ? message : message.substring(0, 497) + "...";
    }

    private static String safeErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        String redacted = redactSensitiveText(message).replaceAll("\\s+", " ").trim();
        return redacted.length() <= 500 ? redacted : redacted.substring(0, 497) + "...";
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

    private static final class TestDesignModelGenerationException extends RuntimeException {

        private final ModelInvocationResult response;

        private TestDesignModelGenerationException(ModelInvocationResult response, RuntimeException cause) {
            super(cause.getMessage(), cause);
            this.response = response;
        }

        private ModelInvocationResult response() {
            return response;
        }
    }
}
