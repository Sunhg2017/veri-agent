package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishRecordResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityDistributionItemResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityMetricResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessCheckResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualitySummaryResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignStepResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskDetailResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

}
