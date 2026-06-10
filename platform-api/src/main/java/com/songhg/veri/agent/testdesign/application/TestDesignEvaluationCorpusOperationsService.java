package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.command.CreateTestDesignEvaluationSampleFromCandidateCommand;
import com.songhg.veri.agent.testdesign.application.command.RequestTestDesignCalibrationRunCommand;
import com.songhg.veri.agent.testdesign.application.command.SaveTestDesignEvaluationSampleCommand;
import com.songhg.veri.agent.testdesign.application.command.TransitionTestDesignEvaluationSampleCommand;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCalibrationRunPageRequest;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCalibrationRunQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignEvaluationSamplePageRequest;
import com.songhg.veri.agent.testdesign.application.query.TestDesignEvaluationSampleQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignPromptTrendRequest;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCalibrationRunResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCalibrationRunsResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCalibrationSummaryResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignEvaluationSampleResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignEvaluationSampleSummaryResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPromptTrendBucketResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPromptTrendResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessResponse;
import com.songhg.veri.agent.testdesign.domain.TestDesignCalibrationRun;
import com.songhg.veri.agent.testdesign.domain.TestDesignCalibrationSummary;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignEvaluationSample;
import com.songhg.veri.agent.testdesign.domain.TestDesignEvaluationSampleSummary;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Maintains the real WP5 evaluation samples and records long-term prompt calibration runs.
 *
 * <p>The operations console may expose curated sample summaries to authorized maintainers, but task diagnostics and
 * reports continue to use aggregate-only policy rows. Calibration runs are bound to a sample baseline digest so prompt
 * changes can be compared over time without exporting sample rows in release reports.</p>
 */
@Service
public class TestDesignEvaluationCorpusOperationsService {

    private static final int MAX_TITLE_CHARS = 256;
    private static final int MAX_SAMPLE_TEXT_CHARS = 2000;
    private static final int MAX_NOTE_CHARS = 1000;
    private static final int MAX_TAGS_CHARS = 512;
    private static final int MAX_CODE_CHARS = 128;
    private static final String STATUS_CANDIDATE = "CANDIDATE";
    private static final String STATUS_GOLDEN = "GOLDEN";
    private static final String STATUS_FROZEN = "FROZEN";
    private static final String STATUS_DEPRECATED = "DEPRECATED";
    private static final String CALIBRATION_PASSED = "PASSED";
    private static final String CALIBRATION_WARNING = "WARNING";
    private static final String CALIBRATION_BLOCKED = "BLOCKED";
    private static final PageQuery BASELINE_PAGE = new PageQuery(0, 1000, 0, "", "%%");
    private static final List<String> SAMPLE_STATUSES = List.of(
            STATUS_CANDIDATE,
            STATUS_GOLDEN,
            STATUS_FROZEN,
            STATUS_DEPRECATED
    );
    private static final List<String> SAMPLE_SOURCE_TYPES = List.of(
            "MANUAL",
            "REVIEW_FEEDBACK",
            "PUBLISHED_CASE",
            "IMPORTED"
    );
    private static final List<String> CALIBRATION_RUN_MODES = List.of(
            "MANUAL",
            "PROMPT_CHANGE",
            "SCHEDULED",
            "BASELINE_FREEZE"
    );

    private final TestDesignRepository repository;
    private final TestDesignQualityService qualityService;
    private final TestDesignActorResolver actorResolver;
    private final TestDesignPlatformContextClient contextClient;

    public TestDesignEvaluationCorpusOperationsService(
            TestDesignRepository repository,
            TestDesignQualityService qualityService,
            TestDesignActorResolver actorResolver,
            TestDesignPlatformContextClient contextClient
    ) {
        this.repository = repository;
        this.qualityService = qualityService;
        this.actorResolver = actorResolver;
        this.contextClient = contextClient;
    }

    @Transactional(readOnly = true)
    public PageResponse<TestDesignEvaluationSampleResponse> samples(TestDesignEvaluationSamplePageRequest request) {
        TestDesignEvaluationSampleQuery query = (request == null ? new TestDesignEvaluationSamplePageRequest() : request)
                .toQuery();
        List<TestDesignEvaluationSampleResponse> items = repository.evaluationSamples(query).stream()
                .map(this::toSampleResponse)
                .toList();
        return PageResponse.of(items, query.index(), query.size(), repository.countEvaluationSamples(query));
    }

    @Transactional(readOnly = true)
    public TestDesignEvaluationSampleSummaryResponse sampleSummary(String projectId, String promptKey) {
        return toSampleSummaryResponse(repository.evaluationSampleSummary(trimToNull(projectId), trimToNull(promptKey)));
    }

    @Transactional
    public TestDesignEvaluationSampleResponse createSample(SaveTestDesignEvaluationSampleCommand command) {
        SaveTestDesignEvaluationSampleCommand safeCommand = requireCommand(command);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        String sampleKey = StringUtils.hasText(safeCommand.sampleKey())
                ? boundedCode(safeCommand.sampleKey(), "sampleKey")
                : "WP5-SAMPLE-" + id.toString().substring(0, 8);
        assertSampleKeyAvailable(safeCommand.projectId(), sampleKey, id);
        TestDesignEvaluationSample sample = buildSample(id, null, safeCommand, sampleKey, actor, actor, now, now);
        TestDesignEvaluationSample saved = repository.saveEvaluationSample(sample);
        writeAudit("EVALUATION_SAMPLE_CREATE", saved, sampleAuditDetails(saved));
        return toSampleResponse(saved);
    }

    @Transactional
    public TestDesignEvaluationSampleResponse updateSample(UUID id, SaveTestDesignEvaluationSampleCommand command) {
        TestDesignEvaluationSample current = sampleOrThrow(id);
        SaveTestDesignEvaluationSampleCommand safeCommand = requireCommand(command);
        if (!current.projectId().equals(safeCommand.projectId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "评测样本不允许跨项目迁移");
        }
        String sampleKey = StringUtils.hasText(safeCommand.sampleKey())
                ? boundedCode(safeCommand.sampleKey(), "sampleKey")
                : current.sampleKey();
        assertSampleKeyAvailable(safeCommand.projectId(), sampleKey, id);
        Instant now = Instant.now();
        TestDesignEvaluationSample updated = buildSample(
                id,
                current,
                safeCommand,
                sampleKey,
                current.createdBy(),
                actorResolver.currentActor(),
                current.createdAt(),
                now
        );
        TestDesignEvaluationSample saved = repository.saveEvaluationSample(updated);
        writeAudit("EVALUATION_SAMPLE_UPDATE", saved, sampleAuditDetails(saved));
        return toSampleResponse(saved);
    }

    @Transactional
    public TestDesignEvaluationSampleResponse transitionSample(
            UUID id,
            TransitionTestDesignEvaluationSampleCommand command
    ) {
        TestDesignEvaluationSample current = sampleOrThrow(id);
        TransitionTestDesignEvaluationSampleCommand safeCommand = command == null
                ? new TransitionTestDesignEvaluationSampleCommand(null, null, null)
                : command;
        String nextStatus = normalizeStatus(safeCommand.status());
        String baselineVersion = baselineVersion(
                StringUtils.hasText(safeCommand.baselineVersion()) ? safeCommand.baselineVersion() : current.baselineVersion(),
                nextStatus
        );
        String maintenanceNote = TestDesignApprovalWorkflowSupport.replacementText(
                safeCommand.maintenanceNote(),
                current.maintenanceNote(),
                "maintenanceNote",
                MAX_NOTE_CHARS,
                true
        );
        Instant now = Instant.now();
        TestDesignEvaluationSample updated = new TestDesignEvaluationSample(
                current.id(),
                current.projectId(),
                current.sampleKey(),
                current.title(),
                current.sourceType(),
                current.sourceTaskId(),
                current.sourceCandidateId(),
                current.promptKey(),
                current.promptVersion(),
                current.coverageType(),
                current.priority(),
                nextStatus,
                baselineVersion,
                current.requirementSummary(),
                current.expectedCaseOutline(),
                current.assertionNotes(),
                current.tags(),
                maintenanceNote,
                sampleDigest(current.sampleKey(), current.title(), current.requirementSummary(),
                        current.expectedCaseOutline(), current.assertionNotes(), baselineVersion, nextStatus),
                current.sensitiveScanStatus(),
                current.createdBy(),
                actorResolver.currentActor(),
                current.createdAt(),
                now
        );
        TestDesignEvaluationSample saved = repository.saveEvaluationSample(updated);
        writeAudit("EVALUATION_SAMPLE_TRANSITION", saved, Map.of(
                "projectId", saved.projectId(),
                "sampleKey", saved.sampleKey(),
                "fromStatus", current.status(),
                "toStatus", saved.status(),
                "baselineVersion", nullToEmpty(saved.baselineVersion())
        ));
        return toSampleResponse(saved);
    }

    @Transactional
    public TestDesignEvaluationSampleResponse createSampleFromCandidate(
            CreateTestDesignEvaluationSampleFromCandidateCommand command
    ) {
        if (command == null || command.candidateId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "candidateId 不能为空");
        }
        TestDesignCandidate candidate = repository.candidate(command.candidateId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "候选用例不存在: " + command.candidateId()));
        TestDesignTask task = repository.task(candidate.taskId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例生成任务不存在: " + candidate.taskId()));
        String sampleKey = StringUtils.hasText(command.sampleKey())
                ? boundedCode(command.sampleKey(), "sampleKey")
                : "WP5-CAND-" + candidate.id().toString().substring(0, 8);
        SaveTestDesignEvaluationSampleCommand createCommand = new SaveTestDesignEvaluationSampleCommand(
                candidate.projectId(),
                sampleKey,
                candidate.title(),
                "REVIEW_FEEDBACK",
                task.id(),
                candidate.id(),
                StringUtils.hasText(candidate.promptKey()) ? candidate.promptKey() : task.promptKey(),
                StringUtils.hasText(candidate.promptVersion()) ? candidate.promptVersion() : task.promptVersion(),
                candidate.coverageType(),
                candidate.priority(),
                command.status(),
                command.baselineVersion(),
                candidate.description(),
                candidate.expectedResult(),
                StringUtils.hasText(candidate.reviewComment())
                        ? candidate.reviewComment()
                        : StringUtils.hasText(candidate.rejectedReason()) ? candidate.rejectedReason() : candidate.ignoredReason(),
                candidate.tags(),
                command.maintenanceNote()
        );
        return createSample(createCommand);
    }

    @Transactional(readOnly = true)
    public TestDesignCalibrationRunsResponse calibrationRuns(TestDesignCalibrationRunPageRequest request) {
        TestDesignCalibrationRunQuery query = (request == null ? new TestDesignCalibrationRunPageRequest() : request)
                .toQuery();
        List<TestDesignCalibrationRunResponse> items = repository.calibrationRuns(query).stream()
                .map(this::toCalibrationRunResponse)
                .toList();
        TestDesignCalibrationSummaryResponse summary = toCalibrationSummaryResponse(
                repository.calibrationSummary(trimToNull(query.projectId()), trimToNull(query.promptKey())),
                repository.evaluationSampleSummary(trimToNull(query.projectId()), trimToNull(query.promptKey()))
        );
        return new TestDesignCalibrationRunsResponse(
                items,
                query.index(),
                query.size(),
                repository.countCalibrationRuns(query),
                summary
        );
    }

    @Transactional
    public TestDesignCalibrationRunResponse runCalibration(RequestTestDesignCalibrationRunCommand command) {
        RequestTestDesignCalibrationRunCommand safeCommand = command == null
                ? new RequestTestDesignCalibrationRunCommand(null, null, null, null, null, null)
                : command;
        String projectId = TestDesignApprovalWorkflowSupport.requiredCode(safeCommand.projectId(), "projectId");
        String promptKey = trimToNull(safeCommand.promptKey());
        String baselineVersion = trimToNull(safeCommand.baselineVersion());
        List<TestDesignEvaluationSample> baselineSamples = baselineSamples(projectId, promptKey, baselineVersion);
        long goldenSampleCount = baselineSamples.stream()
                .filter(sample -> STATUS_GOLDEN.equals(sample.status()) || STATUS_FROZEN.equals(sample.status()))
                .count();
        String promptVersion = resolvePromptVersion(projectId, promptKey, safeCommand.promptVersion());
        TestDesignPromptTrendBucketResponse bucket = promptBucket(projectId, promptKey, promptVersion);
        TestDesignQualityReadinessResponse readiness = bucket == null ? null : bucket.readiness();
        String readinessStatus = readiness == null ? "UNKNOWN" : readiness.status();
        long readinessBlockingCount = readiness == null ? 0L : readiness.blockingCount();
        long readinessWarningCount = readiness == null ? 0L : readiness.warningCount();
        TestDesignCalibrationRun previousRun = repository.latestCalibrationRun(projectId, promptKey, promptVersion)
                .orElse(null);
        long regressionCount = regressionCount(bucket, previousRun);
        String status = calibrationStatus(goldenSampleCount, bucket, readinessStatus, regressionCount);
        Instant now = Instant.now();
        String notes = TestDesignApprovalWorkflowSupport.boundedSafeText(
                safeCommand.notes(), "notes", MAX_NOTE_CHARS, true, false
        );
        String baselineDigest = baselineDigest(baselineSamples);
        String resultDigest = resultDigest(projectId, promptKey, promptVersion, baselineDigest, bucket, readinessStatus,
                regressionCount, status);
        TestDesignCalibrationRun run = new TestDesignCalibrationRun(
                UUID.randomUUID(),
                projectId,
                promptKey,
                promptVersion,
                baselineVersion,
                normalizeRunMode(safeCommand.runMode()),
                status,
                baselineSamples.size(),
                goldenSampleCount,
                bucket == null ? 0L : bucket.taskCount(),
                bucket == null ? 0L : bucket.candidateCount(),
                bucket == null ? 0D : bucket.stepCompletePercent(),
                bucket == null ? 0D : bucket.expectedCompletePercent(),
                bucket == null ? 0D : bucket.lowConfidencePercent(),
                bucket == null ? 0D : bucket.errorPercent(),
                bucket == null ? 0L : bucket.duplicateKeyCollisionCount(),
                bucket == null ? 0L : bucket.correctionCount() + bucket.rejectedCount() + bucket.ignoredCount(),
                readinessStatus,
                readinessBlockingCount,
                readinessWarningCount,
                regressionCount,
                baselineDigest,
                resultDigest,
                notes,
                actorResolver.currentActor(),
                now
        );
        TestDesignCalibrationRun saved = repository.saveCalibrationRun(run);
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("projectId", saved.projectId());
        auditDetails.put("promptKey", nullToEmpty(saved.promptKey()));
        auditDetails.put("promptVersion", nullToEmpty(saved.promptVersion()));
        auditDetails.put("baselineVersion", nullToEmpty(saved.baselineVersion()));
        auditDetails.put("status", saved.status());
        auditDetails.put("sampleCount", saved.sampleCount());
        auditDetails.put("goldenSampleCount", saved.goldenSampleCount());
        auditDetails.put("candidateCount", saved.candidateCount());
        auditDetails.put("regressionCount", saved.regressionCount());
        auditDetails.put("baselineDigest", nullToEmpty(saved.baselineDigest()));
        auditDetails.put("resultDigest", nullToEmpty(saved.resultDigest()));
        writeAudit("PROMPT_CALIBRATION_RUN", saved.projectId(), saved.id(), auditDetails);
        return toCalibrationRunResponse(saved);
    }

    private SaveTestDesignEvaluationSampleCommand requireCommand(SaveTestDesignEvaluationSampleCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "样本请求不能为空");
        }
        return command;
    }

    private TestDesignEvaluationSample buildSample(
            UUID id,
            TestDesignEvaluationSample current,
            SaveTestDesignEvaluationSampleCommand command,
            String sampleKey,
            String createdBy,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt
    ) {
        String projectId = TestDesignApprovalWorkflowSupport.requiredCode(command.projectId(), "projectId");
        String status = normalizeStatus(command.status());
        String baselineVersion = baselineVersion(command.baselineVersion(), status);
        String title = TestDesignApprovalWorkflowSupport.boundedSafeText(
                command.title(), "title", MAX_TITLE_CHARS, false, true
        );
        String requirementSummary = safeSampleText(command.requirementSummary(), "requirementSummary", MAX_SAMPLE_TEXT_CHARS, true);
        String expectedCaseOutline = safeSampleText(command.expectedCaseOutline(), "expectedCaseOutline", MAX_SAMPLE_TEXT_CHARS, true);
        String assertionNotes = safeSampleText(command.assertionNotes(), "assertionNotes", MAX_NOTE_CHARS, false);
        String tags = TestDesignApprovalWorkflowSupport.boundedSafeText(command.tags(), "tags", MAX_TAGS_CHARS, false, false);
        String maintenanceNote = TestDesignApprovalWorkflowSupport.boundedSafeText(
                command.maintenanceNote(), "maintenanceNote", MAX_NOTE_CHARS, true, false
        );
        return new TestDesignEvaluationSample(
                id,
                projectId,
                sampleKey,
                title,
                normalizeSourceType(command.sourceType()),
                command.sourceTaskId() == null && current != null ? current.sourceTaskId() : command.sourceTaskId(),
                command.sourceCandidateId() == null && current != null
                        ? current.sourceCandidateId()
                        : command.sourceCandidateId(),
                boundedOptionalCode(command.promptKey(), "promptKey"),
                boundedOptionalCode(command.promptVersion(), "promptVersion"),
                normalizeCoverageType(command.coverageType()),
                normalizePriority(command.priority()),
                status,
                baselineVersion,
                requirementSummary,
                expectedCaseOutline,
                assertionNotes,
                tags,
                maintenanceNote,
                sampleDigest(sampleKey, title, requirementSummary, expectedCaseOutline, assertionNotes,
                        baselineVersion, status),
                "PASSED",
                createdBy,
                updatedBy,
                createdAt,
                updatedAt
        );
    }

    private TestDesignEvaluationSample sampleOrThrow(UUID id) {
        return repository.evaluationSample(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "评测样本不存在: " + id));
    }

    private void assertSampleKeyAvailable(String projectId, String sampleKey, UUID currentId) {
        repository.evaluationSampleByProjectAndKey(projectId, sampleKey)
                .filter(existing -> !existing.id().equals(currentId))
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.CONFLICT, "样本编号已存在: " + sampleKey);
                });
    }

    private List<TestDesignEvaluationSample> baselineSamples(String projectId, String promptKey, String baselineVersion) {
        TestDesignEvaluationSampleQuery query = new TestDesignEvaluationSampleQuery(
                projectId,
                promptKey,
                null,
                null,
                null,
                baselineVersion,
                null,
                BASELINE_PAGE
        );
        return repository.evaluationSamples(query).stream()
                .filter(sample -> STATUS_GOLDEN.equals(sample.status()) || STATUS_FROZEN.equals(sample.status()))
                .sorted(Comparator.comparing(TestDesignEvaluationSample::sampleKey))
                .toList();
    }

    private TestDesignPromptTrendBucketResponse promptBucket(String projectId, String promptKey, String promptVersion) {
        TestDesignPromptTrendRequest request = new TestDesignPromptTrendRequest();
        request.setProjectId(projectId);
        request.setPromptKey(promptKey);
        request.setSize(100);
        TestDesignPromptTrendResponse trend = qualityService.promptTrend(request);
        if (!StringUtils.hasText(promptVersion)) {
            return trend.buckets().stream()
                    .max(Comparator.comparing(bucket -> bucket.latestTaskCreatedAt() == null
                            ? Instant.EPOCH
                            : bucket.latestTaskCreatedAt()))
                    .orElse(null);
        }
        return trend.buckets().stream()
                .filter(bucket -> promptVersion.equals(bucket.promptVersion()))
                .findFirst()
                .orElse(null);
    }

    private String resolvePromptVersion(String projectId, String promptKey, String requestedPromptVersion) {
        if (StringUtils.hasText(requestedPromptVersion)) {
            return boundedOptionalCode(requestedPromptVersion, "promptVersion");
        }
        TestDesignPromptTrendBucketResponse bucket = promptBucket(projectId, promptKey, null);
        return bucket == null ? null : bucket.promptVersion();
    }

    private static long regressionCount(TestDesignPromptTrendBucketResponse bucket, TestDesignCalibrationRun previousRun) {
        if (bucket == null || previousRun == null) {
            return 0L;
        }
        long regressions = 0L;
        if (bucket.stepCompletePercent() < previousRun.stepCompletePercent()) {
            regressions++;
        }
        if (bucket.expectedCompletePercent() < previousRun.expectedCompletePercent()) {
            regressions++;
        }
        if (bucket.lowConfidencePercent() > previousRun.lowConfidencePercent()) {
            regressions++;
        }
        if (bucket.errorPercent() > previousRun.errorPercent()) {
            regressions++;
        }
        if (bucket.duplicateKeyCollisionCount() > previousRun.duplicateKeyCollisionCount()) {
            regressions++;
        }
        return regressions;
    }

    private static String calibrationStatus(
            long goldenSampleCount,
            TestDesignPromptTrendBucketResponse bucket,
            String readinessStatus,
            long regressionCount
    ) {
        if (goldenSampleCount <= 0 || bucket == null || bucket.candidateCount() <= 0 || "BLOCKED".equals(readinessStatus)) {
            return CALIBRATION_BLOCKED;
        }
        if (regressionCount > 0 || "WARNING".equals(readinessStatus)) {
            return CALIBRATION_WARNING;
        }
        return CALIBRATION_PASSED;
    }

    private static String baselineDigest(List<TestDesignEvaluationSample> samples) {
        StringBuilder payload = new StringBuilder();
        for (TestDesignEvaluationSample sample : samples) {
            payload.append(sample.projectId()).append('|')
                    .append(nullToEmpty(sample.promptKey())).append('|')
                    .append(nullToEmpty(sample.baselineVersion())).append('|')
                    .append(sample.sampleKey()).append('|')
                    .append(nullToEmpty(sample.sampleDigest())).append('|')
                    .append(sample.status()).append('\n');
        }
        return TestDesignApprovalWorkflowSupport.sha256OrNull(payload.toString());
    }

    private static String resultDigest(
            String projectId,
            String promptKey,
            String promptVersion,
            String baselineDigest,
            TestDesignPromptTrendBucketResponse bucket,
            String readinessStatus,
            long regressionCount,
            String status
    ) {
        String payload = projectId + '|'
                + nullToEmpty(promptKey) + '|'
                + nullToEmpty(promptVersion) + '|'
                + nullToEmpty(baselineDigest) + '|'
                + (bucket == null ? 0L : bucket.taskCount()) + '|'
                + (bucket == null ? 0L : bucket.candidateCount()) + '|'
                + (bucket == null ? 0D : bucket.stepCompletePercent()) + '|'
                + (bucket == null ? 0D : bucket.expectedCompletePercent()) + '|'
                + (bucket == null ? 0D : bucket.lowConfidencePercent()) + '|'
                + (bucket == null ? 0D : bucket.errorPercent()) + '|'
                + nullToEmpty(readinessStatus) + '|'
                + regressionCount + '|'
                + status;
        return TestDesignApprovalWorkflowSupport.sha256OrNull(payload);
    }

    private static String normalizeStatus(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : STATUS_CANDIDATE;
        if (!SAMPLE_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "样本状态不在允许范围内");
        }
        return normalized;
    }

    private static String normalizeSourceType(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "MANUAL";
        if (!SAMPLE_SOURCE_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "样本来源类型不在允许范围内");
        }
        return normalized;
    }

    private static String normalizeRunMode(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "MANUAL";
        if (!CALIBRATION_RUN_MODES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "校准运行模式不在允许范围内");
        }
        return normalized;
    }

    private static String normalizeCoverageType(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "FUNCTIONAL";
        if (!List.of("SMOKE", "FUNCTIONAL", "EXCEPTION", "BOUNDARY", "PERMISSION", "REGRESSION")
                .contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "覆盖类型不在允许范围内");
        }
        return normalized;
    }

    private static String normalizePriority(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "MEDIUM";
        if (!List.of("CRITICAL", "HIGH", "MEDIUM", "LOW").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "优先级不在允许范围内");
        }
        return normalized;
    }

    private static String baselineVersion(String value, String status) {
        String normalized = boundedOptionalCode(value, "baselineVersion");
        if ((STATUS_GOLDEN.equals(status) || STATUS_FROZEN.equals(status)) && !StringUtils.hasText(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "GOLDEN/FROZEN 样本必须绑定 baselineVersion");
        }
        return normalized;
    }

    private static String safeSampleText(String value, String fieldName, int maxLength, boolean required) {
        String redacted = TestDesignSensitiveText.redact(value);
        return TestDesignApprovalWorkflowSupport.boundedSafeText(redacted, fieldName, maxLength, true, required);
    }

    private static String boundedCode(String value, String fieldName) {
        String normalized = TestDesignApprovalWorkflowSupport.requiredCode(value, fieldName);
        if (normalized.length() > MAX_CODE_CHARS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能大于 " + MAX_CODE_CHARS);
        }
        return normalized;
    }

    private static String boundedOptionalCode(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return boundedCode(value, fieldName);
    }

    private static String sampleDigest(
            String sampleKey,
            String title,
            String requirementSummary,
            String expectedCaseOutline,
            String assertionNotes,
            String baselineVersion,
            String status
    ) {
        return TestDesignApprovalWorkflowSupport.sha256OrNull(nullToEmpty(sampleKey) + '|'
                + nullToEmpty(title) + '|'
                + nullToEmpty(requirementSummary) + '|'
                + nullToEmpty(expectedCaseOutline) + '|'
                + nullToEmpty(assertionNotes) + '|'
                + nullToEmpty(baselineVersion) + '|'
                + nullToEmpty(status));
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private TestDesignEvaluationSampleResponse toSampleResponse(TestDesignEvaluationSample sample) {
        return new TestDesignEvaluationSampleResponse(
                sample.id(),
                sample.projectId(),
                sample.sampleKey(),
                sample.title(),
                sample.sourceType(),
                sample.sourceTaskId(),
                sample.sourceCandidateId(),
                sample.promptKey(),
                sample.promptVersion(),
                sample.coverageType(),
                sample.priority(),
                sample.status(),
                sample.baselineVersion(),
                sample.requirementSummary(),
                sample.expectedCaseOutline(),
                sample.assertionNotes(),
                sample.tags(),
                sample.maintenanceNote(),
                sample.sampleDigest(),
                sample.sensitiveScanStatus(),
                sample.createdBy(),
                sample.updatedBy(),
                sample.createdAt(),
                sample.updatedAt()
        );
    }

    private static TestDesignEvaluationSampleSummaryResponse toSampleSummaryResponse(
            TestDesignEvaluationSampleSummary summary
    ) {
        long baselineReadyCount = summary.goldenCount() + summary.frozenCount();
        return new TestDesignEvaluationSampleSummaryResponse(
                summary.totalCount(),
                summary.candidateCount(),
                summary.goldenCount(),
                summary.frozenCount(),
                summary.deprecatedCount(),
                summary.baselineVersionCount(),
                summary.latestUpdatedAt(),
                true,
                baselineReadyCount > 0L
        );
    }

    private TestDesignCalibrationRunResponse toCalibrationRunResponse(TestDesignCalibrationRun run) {
        return new TestDesignCalibrationRunResponse(
                run.id(),
                run.projectId(),
                run.promptKey(),
                run.promptVersion(),
                run.baselineVersion(),
                run.runMode(),
                run.status(),
                run.sampleCount(),
                run.goldenSampleCount(),
                run.taskCount(),
                run.candidateCount(),
                run.stepCompletePercent(),
                run.expectedCompletePercent(),
                run.lowConfidencePercent(),
                run.errorPercent(),
                run.duplicateKeyCollisionCount(),
                run.feedbackSignalCount(),
                run.readinessStatus(),
                run.readinessBlockingCount(),
                run.readinessWarningCount(),
                run.regressionCount(),
                run.baselineDigest(),
                run.resultDigest(),
                run.notes(),
                run.runBy(),
                run.createdAt()
        );
    }

    private static TestDesignCalibrationSummaryResponse toCalibrationSummaryResponse(
            TestDesignCalibrationSummary calibrationSummary,
            TestDesignEvaluationSampleSummary sampleSummary
    ) {
        boolean baselineReady = sampleSummary.goldenCount() + sampleSummary.frozenCount() > 0L
                && calibrationSummary.totalRunCount() > 0L;
        return new TestDesignCalibrationSummaryResponse(
                calibrationSummary.totalRunCount(),
                calibrationSummary.passedRunCount(),
                calibrationSummary.warningRunCount(),
                calibrationSummary.blockedRunCount(),
                calibrationSummary.latestStatus(),
                calibrationSummary.latestRunAt(),
                true,
                baselineReady
        );
    }

    private static Map<String, Object> sampleAuditDetails(TestDesignEvaluationSample sample) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("projectId", sample.projectId());
        details.put("sampleKey", sample.sampleKey());
        details.put("status", sample.status());
        details.put("promptKey", nullToEmpty(sample.promptKey()));
        details.put("promptVersion", nullToEmpty(sample.promptVersion()));
        details.put("baselineVersion", nullToEmpty(sample.baselineVersion()));
        details.put("sampleDigest", nullToEmpty(sample.sampleDigest()));
        details.put("sensitiveScanStatus", sample.sensitiveScanStatus());
        return details;
    }

    private void writeAudit(String action, TestDesignEvaluationSample sample, Map<String, Object> after) {
        writeAudit(action, sample.projectId(), sample.id(), after);
    }

    private void writeAudit(String action, String projectId, UUID resourceId, Map<String, Object> after) {
        contextClient.writeAuditEvent(
                action,
                "TEST_DESIGN_EVALUATION_CORPUS",
                resourceId.toString(),
                projectId,
                "SUCCEEDED",
                after
        );
    }
}
