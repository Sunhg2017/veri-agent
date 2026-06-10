package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignEvaluationCorpusSummaryRequest;
import com.songhg.veri.agent.testdesign.application.query.TestDesignPromptTrendRequest;
import com.songhg.veri.agent.testdesign.application.query.TestDesignScopeSummaryRequest;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskQuery;
import com.songhg.veri.agent.testdesign.application.view.TestDesignEvaluationCorpusPolicyResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignEvaluationCorpusSummaryResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditChainMetricResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditChainReadinessResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPromptTrendBucketResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPromptTrendResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishRecordResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityDistributionItemResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityMetricResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessCheckResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualitySummaryResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignScopePolicyResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignScopeSummaryResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignStepResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskDetailResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignCalibrationSummary;
import com.songhg.veri.agent.testdesign.domain.TestDesignEvaluationSampleSummary;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TestDesignQualityService {

    private static final String READINESS_PASSED = "PASSED";
    private static final String READINESS_WARNING = "WARNING";
    private static final String READINESS_BLOCKED = "BLOCKED";
    private static final String READINESS_CHECK_FAILED = "FAILED";
    private static final String READINESS_SEVERITY_BLOCKING = "BLOCKING";
    private static final String READINESS_SEVERITY_WARNING = "WARNING";
    private static final String READINESS_UNIT_COUNT = "COUNT";
    private static final String READINESS_UNIT_PERCENT = "PERCENT";
    private static final String TONE_SUCCESS = "success";
    private static final String TONE_INFO = "info";
    private static final String TONE_WARNING = "warning";
    private final TestDesignRepository repository;
    private final TestDesignResponseMapper responseMapper;
    private final TestDesignProperties properties;

    public TestDesignQualityService(
            TestDesignRepository repository,
            TestDesignResponseMapper responseMapper,
            TestDesignProperties properties
    ) {
        this.repository = repository;
        this.responseMapper = responseMapper;
        this.properties = properties;
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
     * Aggregates recent task quality by Prompt version for WP5 prompt operations.
     *
     * <p>The trend is calculated from task/candidate/review metadata only. It deliberately excludes candidate bodies,
     * review comments, raw prompts and model payloads so operators can compare versions without exposing source text.
     * Each version bucket also reuses the current task readiness thresholds to produce an advisory release signal; this
     * does not alter publish authorization or candidate state.
     */
    public TestDesignPromptTrendResponse promptTrend(TestDesignPromptTrendRequest request) {
        TestDesignPromptTrendRequest safeRequest = request == null ? new TestDesignPromptTrendRequest() : request;
        List<TestDesignTask> tasks = completedTasks(safeRequest.toTaskQuery());
        Map<PromptVersionKey, PromptTrendAccumulator> bucketByVersion = new LinkedHashMap<>();
        for (TestDesignTask task : tasks) {
            PromptTrendAccumulator bucket = bucketByVersion.computeIfAbsent(PromptVersionKey.of(task),
                    key -> new PromptTrendAccumulator(key.promptKey(), key.promptVersion()));
            bucket.acceptTask(task, repository.candidatesByTask(task.id()), repository.reviewRecordsByTask(task.id()));
        }
        long candidateCount = bucketByVersion.values().stream()
                .mapToLong(PromptTrendAccumulator::candidateCount)
                .sum();
        List<TestDesignPromptTrendBucketResponse> buckets = bucketByVersion.values().stream()
                .map(PromptTrendAccumulator::toResponse)
                .toList();
        return new TestDesignPromptTrendResponse(
                trimToNull(safeRequest.getProjectId()),
                trimToNull(safeRequest.getPromptKey()),
                tasks.size(),
                candidateCount,
                promptReadinessDistribution(buckets),
                buckets,
                Instant.now()
        );
    }

    /**
     * Aggregates the evaluation-corpus operating state for release reviewers.
     *
     * <p>The summary intentionally reuses the Prompt trend task window and only inspects task/candidate/review
     * metadata. It proves that the current golden-set baseline, readiness distribution and human feedback signals are
     * visible under project scope, while sample rows, candidate bodies, review comments and Prompt text remain outside
     * the HTTP contract.
     */
    public TestDesignEvaluationCorpusSummaryResponse evaluationCorpusSummary(
            TestDesignEvaluationCorpusSummaryRequest request
    ) {
        TestDesignEvaluationCorpusSummaryRequest safeRequest =
                request == null ? new TestDesignEvaluationCorpusSummaryRequest() : request;
        List<TestDesignTask> tasks = completedTasks(safeRequest.toTaskQuery());
        Map<PromptVersionKey, PromptTrendAccumulator> bucketByVersion = new LinkedHashMap<>();
        EvaluationCorpusFeedbackAccumulator feedback = new EvaluationCorpusFeedbackAccumulator();
        for (TestDesignTask task : tasks) {
            List<TestDesignCandidate> candidates = repository.candidatesByTask(task.id());
            List<TestDesignReviewRecord> reviewRecords = repository.reviewRecordsByTask(task.id());
            PromptTrendAccumulator bucket = bucketByVersion.computeIfAbsent(PromptVersionKey.of(task),
                    key -> new PromptTrendAccumulator(key.promptKey(), key.promptVersion()));
            bucket.acceptTask(task, candidates, reviewRecords);
            feedback.accept(reviewRecords);
        }
        long candidateCount = bucketByVersion.values().stream()
                .mapToLong(PromptTrendAccumulator::candidateCount)
                .sum();
        List<TestDesignPromptTrendBucketResponse> buckets = bucketByVersion.values().stream()
                .map(PromptTrendAccumulator::toResponse)
                .toList();
        TestDesignEvaluationCorpusPolicyResponse policy = TestDesignEvaluationCorpusPolicy.response();
        TestDesignEvaluationSampleSummary sampleSummary = repository.evaluationSampleSummary(
                trimToNull(safeRequest.getProjectId()),
                trimToNull(safeRequest.getPromptKey())
        );
        TestDesignCalibrationSummary calibrationSummary = repository.calibrationSummary(
                trimToNull(safeRequest.getProjectId()),
                trimToNull(safeRequest.getPromptKey())
        );
        boolean baselineReady = sampleSummary.goldenCount() + sampleSummary.frozenCount() > 0L;
        return new TestDesignEvaluationCorpusSummaryResponse(
                trimToNull(safeRequest.getProjectId()),
                trimToNull(safeRequest.getPromptKey()),
                policy,
                tasks.size(),
                candidateCount,
                buckets.size(),
                promptReadinessDistribution(buckets),
                feedback.feedbackSignalCount(),
                feedback.sampleCandidateCount(),
                feedback.sampleExplanationCount(),
                percentValue(feedback.sampleExplanationCount(), feedback.feedbackSignalCount()),
                sampleSummary.totalCount(),
                sampleSummary.goldenCount(),
                sampleSummary.frozenCount(),
                sampleSummary.deprecatedCount(),
                sampleSummary.baselineVersionCount(),
                calibrationSummary.totalRunCount(),
                calibrationSummary.latestStatus(),
                calibrationSummary.latestRunAt(),
                policy.sampleMaintenanceReady(),
                policy.longTermCalibrationReady() && baselineReady && calibrationSummary.totalRunCount() > 0L,
                policy.operationsConsoleReady(),
                policy.aggregateOnly(),
                policy.corpusRowExported(),
                policy.candidateBodyExported(),
                policy.reviewCommentExported(),
                policy.promptBodyExported(),
                Instant.now()
        );
    }

    /**
     * Aggregates WP5 permission and resource-scope signals for platform and project operators.
     *
     * <p>The summary is a read-only operations view. It deliberately counts task/candidate/publish-record project
     * consistency and policy readiness without returning task IDs, candidate IDs, role matrices, sourceRef values,
     * service tokens or authorization rule details.
     */
    public TestDesignScopeSummaryResponse scopeSummary(TestDesignScopeSummaryRequest request) {
        TestDesignScopeSummaryRequest safeRequest =
                request == null ? new TestDesignScopeSummaryRequest() : request;
        ScopeSummaryAccumulator accumulator = new ScopeSummaryAccumulator();
        for (TestDesignTask task : completedTasks(safeRequest.toTaskQuery())) {
            accumulator.accept(
                    task,
                    repository.candidatesByTask(task.id()),
                    repository.publishRecords(task.id())
            );
        }
        return accumulator.toResponse(trimToNull(safeRequest.getProjectId()), trimToNull(safeRequest.getPromptKey()));
    }

    private List<TestDesignTask> completedTasks(TestDesignTaskQuery query) {
        return repository.tasks(query).stream()
                .filter(task -> !TestDesignTaskStatus.QUEUED.name().equals(task.status()))
                .filter(task -> !TestDesignTaskStatus.RUNNING.name().equals(task.status()))
                .toList();
    }

    private PageResponse<TestDesignPublishRecordResponse> publishRecords(UUID taskId) {
        taskOrThrow(taskId);
        Map<UUID, TestDesignCandidate> candidates = candidateById(repository.candidatesByTask(taskId));
        List<TestDesignPublishRecordResponse> records = repository.publishRecords(taskId).stream()
                .map(record -> responseMapper.toPublishRecordResponse(record, candidates.get(record.candidateId())))
                .toList();
        return PageResponse.of(records, 0, Math.max(1, records.size()), records.size());
    }

    private static boolean isPublishableCandidate(TestDesignCandidate candidate) {
        return TestDesignCandidateStatus.CONFIRMED.name().equals(candidate.status())
                || TestDesignCandidateStatus.FAILED.name().equals(candidate.status());
    }

    private Map<UUID, TestDesignCandidate> candidateById(List<TestDesignCandidate> candidates) {
        return candidates.stream().collect(Collectors.toMap(TestDesignCandidate::id, Function.identity()));
    }

    private String stepsJson(List<TestDesignStepResponse> steps) {
        return responseMapper.stepsJson(steps);
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

    private static <T> Map<String, Long> countsBy(List<T> items, Function<T, String> classifier) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (T item : items) {
            String key = classifier.apply(item);
            counts.merge(StringUtils.hasText(key) ? key : "UNKNOWN", 1L, Long::sum);
        }
        return counts;
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
        long reviewableCount = candidates.stream().filter(TestDesignQualityService::isReviewableCandidate).count();
        long publishableCount = candidates.stream().filter(TestDesignQualityService::isPublishableCandidate).count();
        long failedCount = candidates.stream().filter(candidate -> TestDesignCandidateStatus.FAILED.name().equals(candidate.status())).count();
        long confirmedCount = candidates.stream().filter(candidate -> TestDesignCandidateStatus.CONFIRMED.name().equals(candidate.status())).count();
        long publishedCount = candidates.stream().filter(candidate -> TestDesignCandidateStatus.PUBLISHED.name().equals(candidate.status())).count();
        long stepCompleteCount = candidates.stream().filter(this::hasCompleteSteps).count();
        long expectedCompleteCount = candidates.stream().filter(candidate -> StringUtils.hasText(candidate.expectedResult())).count();
        long lowConfidenceCount = candidates.stream().filter(TestDesignQualityService::isLowConfidence).count();
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

    /**
     * Summarizes bucket-level readiness for prompt operations without exposing any candidate or review text.
     */
    private static List<TestDesignQualityDistributionItemResponse> promptReadinessDistribution(
            List<TestDesignPromptTrendBucketResponse> buckets
    ) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TestDesignPromptTrendBucketResponse bucket : buckets) {
            TestDesignQualityReadinessResponse readiness = bucket.readiness();
            String status = readiness == null || !StringUtils.hasText(readiness.status())
                    ? "UNKNOWN"
                    : readiness.status();
            counts.merge(status, 1L, Long::sum);
        }
        return qualityDistribution(counts, buckets.size());
    }

    private static double percentValue(long value, long total) {
        return total <= 0 ? 0D : Math.round(value * 10_000D / total) / 100D;
    }

    private static TestDesignAuditChainMetricResponse scopeMetric(
            String code,
            String label,
            long count,
            String tone
    ) {
        return new TestDesignAuditChainMetricResponse(code, label, count, tone);
    }

    private static TestDesignAuditChainReadinessResponse scopeReadiness(
            String code,
            String label,
            boolean ready,
            String description
    ) {
        return new TestDesignAuditChainReadinessResponse(
                code,
                label,
                ready,
                ready ? TONE_SUCCESS : TONE_WARNING,
                description
        );
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

    private static boolean isPromptTuningCorrection(TestDesignReviewRecord record) {
        return "UPDATE".equals(record.action());
    }

    private static boolean isPromptTuningSignal(TestDesignReviewRecord record) {
        return isPromptTuningCorrection(record)
                || TestDesignCandidateStatus.REJECTED.name().equals(record.action())
                || TestDesignCandidateStatus.IGNORED.name().equals(record.action());
    }

    private TestDesignTask taskOrThrow(UUID id) {
        return repository.task(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例生成任务不存在: " + id));
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

    private static String duplicateKey(UUID requirementId, String coverageType, String title) {
        return requirementId + ":" + coverageType + ":" + (title == null ? "" : title.trim().toLowerCase(Locale.ROOT));
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record PromptVersionKey(String promptKey, String promptVersion) {

        private static PromptVersionKey of(TestDesignTask task) {
            return new PromptVersionKey(
                    StringUtils.hasText(task.promptKey()) ? task.promptKey() : "UNKNOWN",
                    StringUtils.hasText(task.promptVersion()) ? task.promptVersion() : "UNKNOWN"
            );
        }
    }

    private final class PromptTrendAccumulator {

        private final String promptKey;
        private final String promptVersion;
        private long taskCount;
        private long candidateCount;
        private long confirmedCount;
        private long publishedCount;
        private long stepCompleteCount;
        private long expectedCompleteCount;
        private long lowConfidenceCount;
        private long errorCount;
        private long missingRequirementCount;
        private long missingTitleCount;
        private long duplicateKeyCollisionCount;
        private long correctionCount;
        private long rejectedCount;
        private long ignoredCount;
        private Instant latestTaskCreatedAt;

        private PromptTrendAccumulator(String promptKey, String promptVersion) {
            this.promptKey = promptKey;
            this.promptVersion = promptVersion;
        }

        private void acceptTask(
                TestDesignTask task,
                List<TestDesignCandidate> candidates,
                List<TestDesignReviewRecord> reviewRecords
        ) {
            taskCount++;
            if (task.createdAt() != null
                    && (latestTaskCreatedAt == null || task.createdAt().isAfter(latestTaskCreatedAt))) {
                latestTaskCreatedAt = task.createdAt();
            }
            candidateCount += candidates.size();
            confirmedCount += candidates.stream()
                    .filter(candidate -> TestDesignCandidateStatus.CONFIRMED.name().equals(candidate.status()))
                    .count();
            publishedCount += candidates.stream()
                    .filter(candidate -> TestDesignCandidateStatus.PUBLISHED.name().equals(candidate.status()))
                    .count();
            stepCompleteCount += candidates.stream().filter(TestDesignQualityService.this::hasCompleteSteps).count();
            expectedCompleteCount += candidates.stream()
                    .filter(candidate -> StringUtils.hasText(candidate.expectedResult()))
                    .count();
            lowConfidenceCount += candidates.stream()
                    .filter(TestDesignQualityService::isLowConfidence)
                    .count();
            errorCount += candidates.stream()
                    .filter(candidate -> StringUtils.hasText(candidate.errorMessage()))
                    .count();
            missingRequirementCount += candidates.stream()
                    .filter(candidate -> candidate.requirementId() == null)
                    .count();
            missingTitleCount += candidates.stream()
                    .filter(candidate -> !StringUtils.hasText(candidate.title()))
                    .count();
            duplicateKeyCollisionCount += TestDesignQualityService.duplicateKeyCollisionCount(candidates);
            correctionCount += reviewRecords.stream()
                    .filter(TestDesignQualityService::isPromptTuningCorrection)
                    .map(TestDesignReviewRecord::candidateId)
                    .distinct()
                    .count();
            rejectedCount += reviewRecords.stream()
                    .filter(record -> TestDesignCandidateStatus.REJECTED.name().equals(record.action()))
                    .map(TestDesignReviewRecord::candidateId)
                    .distinct()
                    .count();
            ignoredCount += reviewRecords.stream()
                    .filter(record -> TestDesignCandidateStatus.IGNORED.name().equals(record.action()))
                    .map(TestDesignReviewRecord::candidateId)
                    .distinct()
                    .count();
        }

        private long candidateCount() {
            return candidateCount;
        }

        private TestDesignPromptTrendBucketResponse toResponse() {
            long feedbackSignalCount = correctionCount + rejectedCount + ignoredCount;
            return new TestDesignPromptTrendBucketResponse(
                    promptKey,
                    promptVersion,
                    taskCount,
                    candidateCount,
                    confirmedCount,
                    publishedCount,
                    stepCompleteCount,
                    expectedCompleteCount,
                    lowConfidenceCount,
                    errorCount,
                    duplicateKeyCollisionCount,
                    correctionCount,
                    rejectedCount,
                    ignoredCount,
                    percentValue(stepCompleteCount, candidateCount),
                    percentValue(expectedCompleteCount, candidateCount),
                    percentValue(lowConfidenceCount, candidateCount),
                    percentValue(errorCount, candidateCount),
                    percentValue(feedbackSignalCount, candidateCount),
                    qualityReadiness(
                            candidateCount,
                            stepCompleteCount,
                            expectedCompleteCount,
                            lowConfidenceCount,
                            errorCount,
                            missingRequirementCount,
                            missingTitleCount,
                            duplicateKeyCollisionCount
                    ),
                    latestTaskCreatedAt
            );
        }
    }

    private static final class ScopeSummaryAccumulator {

        private final Set<String> projectBuckets = new LinkedHashSet<>();
        private long taskCount;
        private long candidateCount;
        private long publishRecordCount;
        private long candidateScopeMismatchCount;
        private long publishScopeMismatchCount;
        private long modelInvocationReferenceCount;
        private long publishProjectScopeRecordCount;

        private void accept(
                TestDesignTask task,
                List<TestDesignCandidate> candidates,
                List<TestDesignPublishRecord> publishRecords
        ) {
            taskCount++;
            if (StringUtils.hasText(task.projectId())) {
                projectBuckets.add(task.projectId());
            }
            if (task.modelInvocationId() != null) {
                modelInvocationReferenceCount++;
            }
            candidateCount += candidates.size();
            for (TestDesignCandidate candidate : candidates) {
                if (!Objects.equals(task.projectId(), candidate.projectId())) {
                    candidateScopeMismatchCount++;
                }
                if (candidate.modelInvocationId() != null) {
                    modelInvocationReferenceCount++;
                }
            }
            publishRecordCount += publishRecords.size();
            for (TestDesignPublishRecord record : publishRecords) {
                if (StringUtils.hasText(record.projectId())) {
                    publishProjectScopeRecordCount++;
                }
                if (!Objects.equals(task.projectId(), record.projectId())) {
                    publishScopeMismatchCount++;
                }
            }
        }

        private TestDesignScopeSummaryResponse toResponse(String projectId, String promptKey) {
            TestDesignScopePolicyResponse policy = TestDesignScopePolicy.response();
            return new TestDesignScopeSummaryResponse(
                    projectId,
                    promptKey,
                    policy,
                    taskCount,
                    candidateCount,
                    publishRecordCount,
                    projectBuckets.size(),
                    candidateScopeMismatchCount,
                    publishScopeMismatchCount,
                    modelInvocationReferenceCount,
                    publishProjectScopeRecordCount,
                    percentValue(candidateCount - candidateScopeMismatchCount, candidateCount),
                    percentValue(publishRecordCount - publishScopeMismatchCount, publishRecordCount),
                    metrics(),
                    readiness(policy),
                    policy.aggregateOnly(),
                    policy.candidateIdentifierListExported(),
                    policy.roleRuleDetailExported(),
                    policy.serviceTokenValueExported(),
                    Instant.now()
            );
        }

        private List<TestDesignAuditChainMetricResponse> metrics() {
            return List.of(
                    scopeMetric("taskProjectScopes", "任务项目作用域", taskCount,
                            taskCount > 0 ? TONE_SUCCESS : TONE_INFO),
                    scopeMetric("candidateProjectScopes", "候选项目作用域", candidateCount,
                            candidateScopeMismatchCount == 0 ? TONE_SUCCESS : TONE_WARNING),
                    scopeMetric("publishProjectScopes", "发布项目作用域", publishRecordCount,
                            publishScopeMismatchCount == 0 ? TONE_SUCCESS : TONE_WARNING),
                    scopeMetric("projectBuckets", "项目作用域桶", projectBuckets.size(),
                            projectBuckets.isEmpty() ? TONE_INFO : TONE_SUCCESS),
                    scopeMetric("scopeMismatches", "作用域不一致", candidateScopeMismatchCount
                            + publishScopeMismatchCount, candidateScopeMismatchCount + publishScopeMismatchCount == 0
                            ? TONE_SUCCESS : TONE_WARNING),
                    scopeMetric("modelInvocationReferences", "模型调用引用", modelInvocationReferenceCount,
                            modelInvocationReferenceCount > 0 ? TONE_INFO : TONE_INFO),
                    scopeMetric("publishScopeRecords", "发布作用域记录", publishProjectScopeRecordCount,
                            publishProjectScopeRecordCount > 0 ? TONE_SUCCESS : TONE_INFO)
            );
        }

        private List<TestDesignAuditChainReadinessResponse> readiness(TestDesignScopePolicyResponse policy) {
            return List.of(
                    scopeReadiness("taskProjectScopeRequired", "任务项目作用域", policy.taskProjectScopeRequired(),
                            "任务级查询、重试、取消、质量和报告接口按任务归属项目校验"),
                    scopeReadiness("candidateProjectScopeRequired", "候选项目作用域",
                            policy.candidateProjectScopeRequired() && candidateScopeMismatchCount == 0,
                            "候选级和批量候选操作按候选归属项目集合校验"),
                    scopeReadiness("publishProjectScopeRequired", "发布项目作用域",
                            policy.publishProjectScopeRequired() && publishScopeMismatchCount == 0,
                            "发布和 dryRun 按任务项目校验，发布记录项目归属必须与任务一致"),
                    scopeReadiness("evaluationCorpusProjectIsolated", "评测语料项目隔离",
                            policy.evaluationCorpusProjectIsolated(),
                            "评测语料摘要和 AI 评测基线按固定项目作用域隔离"),
                    scopeReadiness("crossWpScopeDashboardReady", "跨 WP 统一作用域看板",
                            policy.crossWpScopeDashboardReady(),
                            "当前只提供 WP5 只读聚合骨架，完整跨 WP scope 看板仍未就绪"),
                    scopeReadiness("detailIdentifiersRedacted", "明细标识不导出",
                            policy.aggregateOnly()
                                    && !policy.candidateIdentifierListExported()
                                    && !policy.roleRuleDetailExported()
                                    && !policy.serviceTokenValueExported(),
                            "候选 ID、角色规则明细和服务令牌原值不进入作用域摘要")
            );
        }
    }

    private static final class EvaluationCorpusFeedbackAccumulator {

        private final Set<UUID> sampleCandidateIds = new LinkedHashSet<>();
        private long feedbackSignalCount;
        private long sampleExplanationCount;

        private void accept(List<TestDesignReviewRecord> reviewRecords) {
            for (TestDesignReviewRecord record : reviewRecords) {
                if (!isPromptTuningSignal(record)) {
                    continue;
                }
                feedbackSignalCount++;
                if (record.candidateId() != null) {
                    sampleCandidateIds.add(record.candidateId());
                }
                if (StringUtils.hasText(record.comment())) {
                    sampleExplanationCount++;
                }
            }
        }

        private long feedbackSignalCount() {
            return feedbackSignalCount;
        }

        private long sampleCandidateCount() {
            return sampleCandidateIds.size();
        }

        private long sampleExplanationCount() {
            return sampleExplanationCount;
        }
    }

}
