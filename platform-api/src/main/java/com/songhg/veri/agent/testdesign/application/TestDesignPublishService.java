package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.command.CreateLinkRequest;
import com.songhg.veri.agent.asset.application.command.CreateTestCaseRequest;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.command.TestDesignPublishCommand;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishRecordResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignStepResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskDetailResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
public class TestDesignPublishService {

    private static final String TEST_CASE_SOURCE_AI_GENERATED = "AI_GENERATED";
    private static final String TEST_CASE_SOURCE_REF_PREFIX = "wp5:";
    private static final String ACTION_RETRY_LINK_EXISTING = "RETRY_LINK_EXISTING";
    static final String ACTION_AUTO_COMPENSATE_LINK_EXISTING = "AUTO_COMPENSATE_LINK_EXISTING";
    private static final String ACTION_DUPLICATE_REVIEW_REQUIRED = "DUPLICATE_REVIEW_REQUIRED";
    private static final String RESULT_CONFLICT = "CONFLICT";
    private static final String RESULT_QUEUED = "QUEUED";
    private static final String READINESS_BLOCKED = "BLOCKED";
    private final TestDesignRepository repository;
    private final AssetService assetService;
    private final TestDesignActorResolver actorResolver;
    private final TestDesignQualityService qualityService;
    private final TestDesignReleaseReadinessApprovalService releaseReadinessApprovalService;
    private final TestDesignResponseMapper responseMapper;
    private final TestDesignProperties properties;
    private final TestDesignEventPublisher eventPublisher;

    public TestDesignPublishService(
            TestDesignRepository repository,
            AssetService assetService,
            TestDesignActorResolver actorResolver,
            TestDesignQualityService qualityService,
            TestDesignReleaseReadinessApprovalService releaseReadinessApprovalService,
            TestDesignResponseMapper responseMapper,
            TestDesignProperties properties,
            TestDesignEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.assetService = assetService;
        this.actorResolver = actorResolver;
        this.qualityService = qualityService;
        this.releaseReadinessApprovalService = releaseReadinessApprovalService;
        this.responseMapper = responseMapper;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    private TestDesignTaskDetailResponse task(UUID id) {
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
     * <p>Formal publish defaults to a local durable queue plus platform event. This keeps the reviewer-facing API from
     * holding a cross-WP transaction open while preserving a synchronous fallback for rollback or test compatibility.
     */
    @Transactional
    public TestDesignPublishResponse publish(UUID taskId, TestDesignPublishCommand command) {
        TestDesignTask task = taskOrThrow(taskId);
        boolean dryRun = command != null && Boolean.TRUE.equals(command.dryRun());
        List<TestDesignCandidate> selected = selectPublishCandidates(task, command == null ? null : command.candidateIds());
        if (selected.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "没有已确认候选用例可发布");
        }
        assertOfficialPublishCandidatesReady(dryRun, command == null ? null : command.candidateIds(), selected);
        if (dryRun) {
            return previewPublish(task, selected);
        }
        validateReleaseReadiness(task.id());
        if (properties.asyncPublishEnabled()) {
            return queuePublish(task, selected);
        }
        return publishSynchronously(task, selected);
    }

    /**
     * Processes one queued publish event after the API transaction commits.
     *
     * <p>The event payload carries the exact candidate IDs that were selected by the reviewer. Recovery re-emits only
     * candidates that remain in PUBLISH_QUEUED, so a retry cannot accidentally expand an explicit publish request to
     * newly confirmed candidates on the same task.
     */
    @Transactional
    public TestDesignPublishResponse processQueuedPublish(UUID taskId, List<UUID> candidateIds) {
        TestDesignTask task = taskOrThrow(taskId);
        List<TestDesignCandidate> selected = selectQueuedPublishCandidates(task, candidateIds);
        if (selected.isEmpty()) {
            return publishResponse(task, false, List.of());
        }
        if (TestDesignTaskStatus.PUBLISH_QUEUED.name().equals(task.status())) {
            repository.markTaskStatus(
                    task.id(),
                    TestDesignTaskStatus.PUBLISH_QUEUED,
                    TestDesignTaskStatus.PUBLISHING,
                    Instant.now()
            );
        }
        TestDesignTask runningTask = taskOrThrow(task.id());
        List<TestDesignPublishRecord> records = new ArrayList<>();
        String actor = actorResolver.currentActor();
        for (TestDesignCandidate candidate : selected) {
            Optional<TestDesignCandidate> claimed = claimQueuedPublishCandidate(candidate);
            if (claimed.isEmpty()) {
                continue;
            }
            records.add(publishQueuedCandidate(runningTask, claimed.get(), actor));
        }
        records.forEach(repository::savePublishRecord);
        refreshTaskCountsAfterPublish(task.id(), task.status());
        return publishResponse(taskOrThrow(task.id()), false, records);
    }

    private TestDesignPublishResponse previewPublish(TestDesignTask task, List<TestDesignCandidate> selected) {
        List<TestDesignPublishRecord> records = new ArrayList<>();
        String actor = actorResolver.currentActor();
        for (TestDesignCandidate candidate : selected) {
            records.add(plannedPublishRecord(task, candidate, actor));
        }
        return publishResponse(task, true, records);
    }

    private TestDesignPublishResponse queuePublish(TestDesignTask task, List<TestDesignCandidate> selected) {
        List<TestDesignPublishRecord> records = new ArrayList<>();
        List<UUID> queuedCandidateIds = new ArrayList<>();
        String actor = actorResolver.currentActor();
        for (TestDesignCandidate candidate : selected) {
            if (candidate.status().equals(TestDesignCandidateStatus.PUBLISHED.name()) && candidate.assetCaseId() != null) {
                records.add(publishRecord(task, candidate, false, "SKIP_PUBLISHED", "SKIPPED",
                        candidate.assetCaseId(), null, actor));
                continue;
            }
            if (!isPublishableCandidate(candidate)) {
                records.add(publishRecord(task, candidate, false, "SKIP_UNCONFIRMED", "SKIPPED",
                        null, "候选用例未确认", actor));
                continue;
            }
            TestDesignCandidate queued = withCandidateStatus(
                    candidate,
                    TestDesignCandidateStatus.PUBLISH_QUEUED,
                    candidate.assetCaseId(),
                    null
            );
            repository.saveCandidate(queued);
            queuedCandidateIds.add(queued.id());
            records.add(publishRecord(task, queued, false, queuedPublishAction(candidate), RESULT_QUEUED,
                    queued.assetCaseId(), null, actor));
        }
        TestDesignTask queuedTask = task;
        if (!queuedCandidateIds.isEmpty()) {
            queuedTask = withTaskStatus(task, TestDesignTaskStatus.PUBLISH_QUEUED, null);
            repository.saveTask(queuedTask);
            eventPublisher.publishPublishRequested(task.id(), queuedCandidateIds);
        }
        return publishResponse(queuedTask, false, records);
    }

    private TestDesignPublishResponse publishSynchronously(TestDesignTask task, List<TestDesignCandidate> selected) {
        repository.saveTask(withTaskStatus(task, TestDesignTaskStatus.PUBLISHING, null));
        List<TestDesignPublishRecord> records = new ArrayList<>();
        String actor = actorResolver.currentActor();
        for (TestDesignCandidate candidate : selected) {
            records.add(publishCandidate(task, candidate, actor, null));
        }
        records.forEach(repository::savePublishRecord);
        refreshTaskCountsAfterPublish(task.id(), task.status());
        return publishResponse(taskOrThrow(task.id()), false, records);
    }

    private void assertOfficialPublishCandidatesReady(boolean dryRun, List<UUID> requestedCandidateIds, List<TestDesignCandidate> selected) {
        if (dryRun || requestedCandidateIds == null || requestedCandidateIds.isEmpty()) {
            return;
        }
        selected.stream()
                .filter(candidate -> !isOfficialPublishReady(candidate))
                .findFirst()
                .ifPresent(candidate -> {
                    /*
                     * A formal publish is an irreversible WP3 write command. When reviewers explicitly select a
                     * candidate, fail before any asset write if that candidate is not in the reviewed publish pool.
                     */
                    throw new BusinessException(ErrorCode.INVALID_STATE, "候选用例未确认，不能发布: " + candidate.id());
                });
    }

    /**
     * Fails closed before any WP3 write when release-readiness blocking is enabled.
     *
     * <p>The check deliberately consumes aggregate readiness only. Dry-run remains available for reviewers to inspect
     * planned publish actions without persisting publish records or leaking candidate-level quality evidence.
     */
    private void validateReleaseReadiness(UUID taskId) {
        if (!properties.releaseReadinessPublishBlockingEnabled()) {
            return;
        }
        TestDesignQualityReadinessResponse readiness = qualityService.qualitySummary(taskId).readiness();
        if (readiness != null && READINESS_BLOCKED.equals(readiness.status())) {
            if (releaseReadinessApprovalService.hasApprovedExceptionForCurrentReadiness(taskId, readiness)) {
                return;
            }
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "WP5 发布准出质量门禁不通过: readiness=BLOCKED, blockingCount=" + readiness.blockingCount());
        }
    }

    private Optional<TestDesignCandidate> claimQueuedPublishCandidate(TestDesignCandidate candidate) {
        if (!TestDesignCandidateStatus.PUBLISH_QUEUED.name().equals(candidate.status())) {
            return Optional.empty();
        }
        /*
         * The candidate-level claim is the real idempotency guard. Task status is only an aggregate progress signal,
         * while each selected candidate must be claimed once before WP3 writes can start.
         */
        boolean claimed = repository.markCandidateStatus(
                candidate.id(),
                TestDesignCandidateStatus.PUBLISH_QUEUED,
                TestDesignCandidateStatus.PUBLISHING,
                Instant.now()
        );
        if (!claimed) {
            return Optional.empty();
        }
        return repository.candidate(candidate.id())
                .filter(current -> TestDesignCandidateStatus.PUBLISHING.name().equals(current.status()));
    }

    private TestDesignPublishRecord publishQueuedCandidate(
            TestDesignTask task,
            TestDesignCandidate candidate,
            String actor
    ) {
        String action = queuedPublishAction(candidate);
        try {
            TestDesignPublishRecord record = publishCandidate(task, candidate, actor, action);
            if (RESULT_CONFLICT.equals(record.result())) {
                TestDesignCandidate failed = withFailedCandidate(candidate, record.assetCaseId(), record.errorMessage());
                repository.saveCandidate(failed);
                /*
                 * A duplicate-review conflict is a terminal publish attempt, not an in-flight state. Mark it failed so
                 * operators can resolve it explicitly and recovery scans will not keep replaying the same conflict.
                 */
                return publishRecord(task, failed, false, record.action(), record.result(),
                        record.assetCaseId(), record.errorMessage(), actor);
            }
            return record;
        } catch (RuntimeException exception) {
            String message = TestDesignGenerationService.safeErrorMessage(exception);
            TestDesignCandidate failed = withFailedCandidate(candidate, candidate.assetCaseId(), message);
            repository.saveCandidate(failed);
            return publishRecord(task, failed, false, action, "FAILED",
                    candidate.assetCaseId(), message, actor);
        }
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
     * Repairs a partial publish where WP3 already has the AI-generated case but WP5 still marks the candidate failed.
     *
     * <p>The compensation backend deliberately avoids high-similarity conflicts and first-time creates. It only replays
     * the idempotent sourceRef lookup and trace-link repair path so automated recovery cannot publish new assets beyond
     * what a prior publish attempt already created.
     */
    @Transactional
    public TestDesignPublishRecord compensateFailedLinkedCandidate(TestDesignCandidate candidate, String actor) {
        repository.lockPublishCompensationCandidate(candidate.id());
        TestDesignCandidate current = repository.candidate(candidate.id()).orElse(candidate);
        TestDesignTask task = taskOrThrow(current.taskId());
        List<TestDesignPublishRecord> records = repository.publishRecords(task.id());
        if (hasSucceededRecord(records, current.id())) {
            return publishRecord(task, current, false, ACTION_AUTO_COMPENSATE_LINK_EXISTING, "SKIPPED",
                    current.assetCaseId(), "候选已有成功发布记录", actor);
        }
        if (hasAutoCompensationRecord(records, current.id())) {
            return publishRecord(task, current, false, ACTION_AUTO_COMPENSATE_LINK_EXISTING, "SKIPPED",
                    current.assetCaseId(), "候选已执行自动补偿尝试", actor);
        }
        if (!TestDesignCandidateStatus.FAILED.name().equals(current.status()) || current.assetCaseId() == null) {
            return publishRecord(task, current, false, ACTION_AUTO_COMPENSATE_LINK_EXISTING, "SKIPPED",
                    current.assetCaseId(), "候选不满足发布补偿条件", actor);
        }
        Optional<TestCaseResponse> existingTestCase = existingWp5TestCase(current)
                .filter(testCase -> Objects.equals(current.assetCaseId(), testCase.id()));
        if (existingTestCase.isEmpty()) {
            TestDesignPublishRecord record = publishRecord(task, current, false, ACTION_AUTO_COMPENSATE_LINK_EXISTING,
                    "FAILED", current.assetCaseId(), "未找到匹配的 WP5 发布源用例，需人工重试", actor);
            repository.savePublishRecord(record);
            return record;
        }
        try {
            TestCaseResponse testCase = existingTestCase.get();
            ensureTraceLink(current, testCase);
            TestDesignCandidate linked = withPublishedCandidate(current, testCase.id(), null);
            repository.saveCandidate(linked);
            TestDesignPublishRecord record = publishRecord(task, linked, false, ACTION_AUTO_COMPENSATE_LINK_EXISTING,
                    "SUCCEEDED", testCase.id(), null, actor);
            repository.savePublishRecord(record);
            refreshTaskCountsAfterPublish(task.id(), task.status());
            return record;
        } catch (BusinessException exception) {
            TestDesignCandidate failed = withFailedCandidate(current, current.assetCaseId(), exception.getMessage());
            repository.saveCandidate(failed);
            TestDesignPublishRecord record = publishRecord(task, failed, false, ACTION_AUTO_COMPENSATE_LINK_EXISTING,
                    "FAILED", current.assetCaseId(), exception.getMessage(), actor);
            repository.savePublishRecord(record);
            return record;
        }
    }

    private boolean hasSucceededRecord(List<TestDesignPublishRecord> records, UUID candidateId) {
        return records.stream()
                .anyMatch(record -> candidateId.equals(record.candidateId()) && "SUCCEEDED".equals(record.result()));
    }

    private boolean hasAutoCompensationRecord(List<TestDesignPublishRecord> records, UUID candidateId) {
        return records.stream()
                .anyMatch(record -> candidateId.equals(record.candidateId())
                        && ACTION_AUTO_COMPENSATE_LINK_EXISTING.equals(record.action()));
    }

    private TestDesignPublishRecord publishCandidate(
            TestDesignTask task,
            TestDesignCandidate candidate,
            String actor,
            String requestedAction
    ) {
        if (candidate.status().equals(TestDesignCandidateStatus.PUBLISHED.name()) && candidate.assetCaseId() != null) {
            return publishRecord(task, candidate, false, "SKIP_PUBLISHED", "SKIPPED", candidate.assetCaseId(), null, actor);
        }
        if (!isExecutablePublishCandidate(candidate)) {
            return publishRecord(task, candidate, false, "SKIP_UNCONFIRMED", "SKIPPED", null, "候选用例未确认", actor);
        }
        Optional<TestCaseResponse> existingTestCase = existingWp5TestCase(candidate);
        if (existingTestCase.isPresent()) {
            TestCaseResponse testCase = existingTestCase.get();
            String action = StringUtils.hasText(requestedAction) ? requestedAction
                    : TestDesignCandidateStatus.FAILED.name().equals(candidate.status())
                    ? ACTION_RETRY_LINK_EXISTING : "LINK_EXISTING";
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

    private static boolean isExecutablePublishCandidate(TestDesignCandidate candidate) {
        return isPublishableCandidate(candidate)
                || TestDesignCandidateStatus.PUBLISHING.name().equals(candidate.status());
    }

    private static boolean isOfficialPublishReady(TestDesignCandidate candidate) {
        return isPublishableCandidate(candidate)
                || (TestDesignCandidateStatus.PUBLISHED.name().equals(candidate.status())
                && candidate.assetCaseId() != null);
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

    private static String queuedPublishAction(TestDesignCandidate candidate) {
        if ((TestDesignCandidateStatus.FAILED.name().equals(candidate.status())
                || TestDesignCandidateStatus.PUBLISHING.name().equals(candidate.status()))
                && candidate.assetCaseId() != null) {
            return ACTION_RETRY_LINK_EXISTING;
        }
        return "CREATE";
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
        boolean hasQueuedPublish = candidates.stream()
                .anyMatch(candidate -> TestDesignCandidateStatus.PUBLISH_QUEUED.name().equals(candidate.status())
                        || TestDesignCandidateStatus.PUBLISHING.name().equals(candidate.status()));
        String status;
        if (hasQueuedPublish) {
            status = task.status();
        } else if (publishedCount > 0 && publishedCount == generatedCount) {
            status = TestDesignTaskStatus.PUBLISHED.name();
        } else {
            status = task.status();
        }
        return new TestDesignTask(
                task.id(), task.projectId(), task.title(), status, task.requirementIds(), task.coverageTypes(),
                task.promptKey(), task.promptVersion(), task.modelInvocationId(), task.modelProviderName(),
                task.modelName(), task.totalRequirements(), generatedCount, confirmedCount, publishedCount,
                task.errorMessage(), task.requestedBy(), task.idempotencyKey(), task.requestDigest(),
                task.inputDigest(), task.contextSummaryJson(), task.createdAt(), Instant.now()
        );
    }

    /**
     * Publish conflicts or partial publish results must not leave the task stuck in transient queue states.
     */
    private void refreshTaskCountsAfterPublish(UUID taskId, String fallbackStatus) {
        TestDesignTask task = taskOrThrow(taskId);
        List<TestDesignCandidate> candidates = repository.candidatesByTask(taskId);
        TestDesignTask counted = withTaskCounts(task, candidates);
        if (hasInFlightPublishCandidate(candidates)) {
            repository.saveTask(counted);
            return;
        }
        if (!isTransientPublishTaskStatus(counted.status())) {
            repository.saveTask(counted);
            return;
        }
        TestDesignTaskStatus completedStatus = completedPublishFallbackStatus(fallbackStatus);
        repository.saveTask(new TestDesignTask(
                counted.id(), counted.projectId(), counted.title(), completedStatus.name(), counted.requirementIds(),
                counted.coverageTypes(), counted.promptKey(), counted.promptVersion(), counted.modelInvocationId(),
                counted.modelProviderName(), counted.modelName(), counted.totalRequirements(), counted.generatedCount(),
                counted.confirmedCount(), counted.publishedCount(), counted.errorMessage(), counted.requestedBy(),
                counted.idempotencyKey(), counted.requestDigest(), counted.inputDigest(), counted.contextSummaryJson(),
                counted.createdAt(), Instant.now()
        ));
    }

    private static boolean isTransientPublishTaskStatus(String status) {
        return TestDesignTaskStatus.PUBLISH_QUEUED.name().equals(status)
                || TestDesignTaskStatus.PUBLISHING.name().equals(status);
    }

    private static TestDesignTaskStatus completedPublishFallbackStatus(String fallbackStatus) {
        if (TestDesignTaskStatus.PARTIAL_SUCCESS.name().equals(fallbackStatus)) {
            return TestDesignTaskStatus.PARTIAL_SUCCESS;
        }
        return TestDesignTaskStatus.SUCCEEDED;
    }

    private static boolean hasInFlightPublishCandidate(List<TestDesignCandidate> candidates) {
        return candidates.stream()
                .anyMatch(candidate -> TestDesignCandidateStatus.PUBLISH_QUEUED.name().equals(candidate.status())
                        || TestDesignCandidateStatus.PUBLISHING.name().equals(candidate.status()));
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

    private TestDesignCandidate withCandidateStatus(
            TestDesignCandidate candidate,
            TestDesignCandidateStatus status,
            UUID assetCaseId,
            String errorMessage
    ) {
        return new TestDesignCandidate(
                candidate.id(), candidate.taskId(), candidate.projectId(), candidate.requirementId(), candidate.apiId(),
                candidate.title(), candidate.description(), candidate.coverageType(), candidate.priority(),
                status.name(), candidate.preconditions(), candidate.stepsJson(), candidate.expectedResult(),
                candidate.tags(), candidate.duplicateKey(), candidate.confidence(), candidate.promptKey(),
                candidate.promptVersion(), candidate.modelInvocationId(), candidate.modelProviderName(),
                candidate.modelName(), assetCaseId, candidate.reviewComment(), candidate.rejectedReason(),
                candidate.ignoredReason(), errorMessage, candidate.confirmedBy(), candidate.confirmedAt(),
                candidate.version() + 1, candidate.createdAt(), Instant.now()
        );
    }

    private TestDesignTask withTaskStatus(TestDesignTask task, TestDesignTaskStatus status, String errorMessage) {
        return new TestDesignTask(
                task.id(), task.projectId(), task.title(), status.name(), task.requirementIds(), task.coverageTypes(),
                task.promptKey(), task.promptVersion(), task.modelInvocationId(), task.modelProviderName(),
                task.modelName(), task.totalRequirements(), task.generatedCount(), task.confirmedCount(),
                task.publishedCount(), errorMessage, task.requestedBy(), task.idempotencyKey(), task.requestDigest(),
                task.inputDigest(), task.contextSummaryJson(), task.createdAt(), Instant.now()
        );
    }

    private TestDesignPublishResponse publishResponse(
            TestDesignTask task,
            boolean dryRun,
            List<TestDesignPublishRecord> records
    ) {
        List<TestDesignPublishRecord> safeRecords = records == null ? List.of() : records;
        Map<UUID, TestDesignCandidate> candidates = candidateById(repository.candidatesByTask(task.id()));
        List<UUID> createdCaseIds = safeRecords.stream()
                .filter(record -> "CREATE".equals(record.action()))
                .filter(record -> "SUCCEEDED".equals(record.result()))
                .map(TestDesignPublishRecord::assetCaseId)
                .filter(Objects::nonNull)
                .toList();
        List<TestDesignPublishRecordResponse> responses = safeRecords.stream()
                .map(record -> responseMapper.toPublishRecordResponse(record, candidates.get(record.candidateId())))
                .toList();
        return new TestDesignPublishResponse(
                task.id(),
                task.projectId(),
                dryRun,
                safeRecords.size(),
                Math.toIntExact(safeRecords.stream()
                        .filter(record -> "CREATE".equals(record.action()))
                        .filter(record -> dryRun || "SUCCEEDED".equals(record.result()) || "PLANNED".equals(record.result()))
                        .count()),
                Math.toIntExact(safeRecords.stream().filter(record -> "SKIPPED".equals(record.result())).count()),
                Math.toIntExact(safeRecords.stream().filter(record -> "FAILED".equals(record.result())).count()),
                createdCaseIds,
                responses
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

    private List<TestDesignCandidate> selectQueuedPublishCandidates(TestDesignTask task, List<UUID> candidateIds) {
        List<TestDesignCandidate> candidates = repository.candidatesByTask(task.id());
        Map<UUID, TestDesignCandidate> candidateById = candidateById(candidates);
        List<TestDesignCandidate> selected;
        if (candidateIds == null || candidateIds.isEmpty()) {
            selected = candidates.stream()
                    .filter(candidate -> TestDesignCandidateStatus.PUBLISH_QUEUED.name().equals(candidate.status()))
                    .sorted(Comparator.comparing(TestDesignCandidate::updatedAt))
                    .toList();
        } else {
            selected = candidateIds.stream()
                    .distinct()
                    .map(id -> {
                        TestDesignCandidate candidate = candidateById.get(id);
                        if (candidate == null) {
                            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "候选用例不属于当前任务: " + id);
                        }
                        return candidate;
                    })
                    .filter(candidate -> TestDesignCandidateStatus.PUBLISH_QUEUED.name().equals(candidate.status()))
                    .toList();
        }
        /*
         * Only PUBLISH_QUEUED candidates are selected. CONFIRMED candidates are deliberately ignored here so recovery
         * and duplicate broker delivery can never publish candidates that were not in the original durable queue.
         */
        return selected;
    }

    private List<CreateTestCaseRequest.StepDto> toAssetSteps(TestDesignCandidate candidate) {
        return responseMapper.steps(candidate.stepsJson()).stream()
                .map(step -> new CreateTestCaseRequest.StepDto(step.action(), step.expectedResult()))
                .toList();
    }

    private Map<UUID, TestDesignCandidate> candidateById(List<TestDesignCandidate> candidates) {
        return candidates.stream().collect(Collectors.toMap(TestDesignCandidate::id, Function.identity()));
    }

    private String stepsJson(List<TestDesignStepResponse> steps) {
        return responseMapper.stepsJson(steps);
    }

    private TestDesignTask taskOrThrow(UUID id) {
        return repository.task(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例生成任务不存在: " + id));
    }

    private static String preconditions(RequirementResponse requirement) {
        if (StringUtils.hasText(requirement.acceptanceCriteria())) {
            return "需求验收标准已明确，测试前需准备满足业务上下文的数据";
        }
        return "需求描述已确认，测试数据和账号权限已准备";
    }

    private static String duplicateKey(UUID requirementId, String coverageType, String title) {
        return requirementId + ":" + coverageType + ":" + (title == null ? "" : title.trim().toLowerCase(Locale.ROOT));
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

}
