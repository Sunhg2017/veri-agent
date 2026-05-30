package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.CsvEncoder;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.view.TestDesignModelObservationResponse;
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
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TestDesignTaskReportService {

    private static final String READINESS_PASSED = "PASSED";
    private static final String READINESS_WARNING = "WARNING";
    private static final String READINESS_BLOCKED = "BLOCKED";
    private static final String READINESS_CHECK_FAILED = "FAILED";
    private static final String READINESS_SEVERITY_BLOCKING = "BLOCKING";
    private static final String READINESS_SEVERITY_WARNING = "WARNING";
    private static final String READINESS_UNIT_COUNT = "COUNT";
    private static final String READINESS_UNIT_PERCENT = "PERCENT";
    private final TestDesignRepository repository;
    private final TestDesignPlatformContextClient contextClient;
    private final TestDesignResponseMapper responseMapper;
    private final TestDesignProperties properties;
    private final ObjectMapper objectMapper;

    public TestDesignTaskReportService(
            TestDesignRepository repository,
            TestDesignPlatformContextClient contextClient,
            TestDesignResponseMapper responseMapper,
            TestDesignProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.contextClient = contextClient;
        this.responseMapper = responseMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
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
    private TestDesignQualitySummaryResponse qualitySummary(UUID id) {
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

    private static boolean hasCandidateReviewNote(TestDesignCandidate candidate) {
        return StringUtils.hasText(candidate.reviewComment())
                || StringUtils.hasText(candidate.rejectedReason())
                || StringUtils.hasText(candidate.ignoredReason());
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
        long reviewNotePresentCount = candidates.stream().filter(TestDesignTaskReportService::hasCandidateReviewNote).count();

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
        Set<UUID> promptTuningCandidateIds = new LinkedHashSet<>();
        long correctionCount = 0L;
        long rejectedCount = 0L;
        long ignoredCount = 0L;
        long promptTuningCommentCount = 0L;
        for (TestDesignReviewRecord record : records) {
            ReviewDiffSummary diffSummary = reviewDiffSummary(record.diffJson());
            diffSummary.changedFields().forEach(field -> changedFieldCounts.merge(field, 1L, Long::sum));
            boolean correction = isPromptTuningCorrection(record, diffSummary);
            boolean rejected = TestDesignCandidateStatus.REJECTED.name().equals(record.action());
            boolean ignored = TestDesignCandidateStatus.IGNORED.name().equals(record.action());
            if (correction) {
                correctionCount++;
            }
            if (rejected) {
                rejectedCount++;
            }
            if (ignored) {
                ignoredCount++;
            }
            if (correction || rejected || ignored) {
                if (record.candidateId() != null) {
                    promptTuningCandidateIds.add(record.candidateId());
                }
                if (StringUtils.hasText(record.comment())) {
                    promptTuningCommentCount++;
                }
            }
        }
        long promptTuningSignalCount = correctionCount + rejectedCount + ignoredCount;

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
        appendTaskReportFeedbackLoopRows(csv, task, generatedAt, total, promptTuningSignalCount,
                promptTuningCandidateIds.size(), correctionCount, rejectedCount, ignoredCount, promptTuningCommentCount);
    }

    /**
     * Appends aggregate-only human feedback loop counters for prompt/sample operations.
     *
     * <p>Prompt tuning needs to know where humans corrected, rejected or ignored AI candidates, but the report must not
     * copy review comments. The rows below expose only counts and percentages so they can be archived with the task
     * report without leaking reviewer text or source requirement content.
     */
    private static void appendTaskReportFeedbackLoopRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            long totalReviewRecords,
            long promptTuningSignalCount,
            long promptTuningCandidateCount,
            long correctionCount,
            long rejectedCount,
            long ignoredCount,
            long promptTuningCommentCount
    ) {
        appendTaskReportRow(csv, task, generatedAt, "metadata", "feedbackLoop", "scope", null,
                "fullTask", null, null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "summary", "feedbackLoop", "metric", "promptTuningSignals",
                promptTuningSignalCount, percent(promptTuningSignalCount, totalReviewRecords), feedbackTone(
                        promptTuningSignalCount, rejectedCount, ignoredCount, promptTuningCommentCount), "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "summary", "feedbackLoop", "metric", "sampleCandidates",
                promptTuningCandidateCount, percent(promptTuningCandidateCount, totalReviewRecords), null, "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "summary", "feedbackLoop", "metric", "commentCoverage",
                promptTuningCommentCount, percent(promptTuningCommentCount, promptTuningSignalCount),
                promptTuningSignalCount == 0L || promptTuningCommentCount * 100D / promptTuningSignalCount >= 80D
                        ? "success" : "warning", "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "summary", "feedbackLoop", "distribution:signal", "correction",
                correctionCount, percent(correctionCount, totalReviewRecords), correctionCount > 0 ? "info" : "neutral",
                "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "summary", "feedbackLoop", "distribution:signal", "rejected",
                rejectedCount, percent(rejectedCount, totalReviewRecords), rejectedCount > 0 ? "warning" : "neutral",
                "fullTask", null);
        appendTaskReportRow(csv, task, generatedAt, "summary", "feedbackLoop", "distribution:signal", "ignored",
                ignoredCount, percent(ignoredCount, totalReviewRecords), ignoredCount > 0 ? "warning" : "neutral",
                "fullTask", null);
        appendTaskReportWarning(csv, task, generatedAt, "feedbackLoop", "promptTuningMissingComment",
                Math.max(promptTuningSignalCount - promptTuningCommentCount, 0L), promptTuningSignalCount);
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
        long reviewableCount = candidates.stream().filter(TestDesignTaskReportService::isReviewableCandidate).count();
        long publishableCount = candidates.stream().filter(TestDesignTaskReportService::isPublishableCandidate).count();
        long failedCount = candidates.stream().filter(candidate -> TestDesignCandidateStatus.FAILED.name().equals(candidate.status())).count();
        long confirmedCount = candidates.stream().filter(candidate -> TestDesignCandidateStatus.CONFIRMED.name().equals(candidate.status())).count();
        long publishedCount = candidates.stream().filter(candidate -> TestDesignCandidateStatus.PUBLISHED.name().equals(candidate.status())).count();
        long stepCompleteCount = candidates.stream().filter(this::hasCompleteSteps).count();
        long expectedCompleteCount = candidates.stream().filter(candidate -> StringUtils.hasText(candidate.expectedResult())).count();
        long lowConfidenceCount = candidates.stream().filter(TestDesignTaskReportService::isLowConfidence).count();
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

    private static boolean isPromptTuningCorrection(TestDesignReviewRecord record, ReviewDiffSummary diffSummary) {
        return "UPDATE".equals(record.action())
                && diffSummary.changedFields().stream()
                .anyMatch(field -> !"status".equals(field) && !"version".equals(field));
    }

    private static String feedbackTone(
            long promptTuningSignalCount,
            long rejectedCount,
            long ignoredCount,
            long promptTuningCommentCount
    ) {
        if (promptTuningSignalCount == 0L) {
            return "neutral";
        }
        if (promptTuningCommentCount * 100D / promptTuningSignalCount < 50D) {
            return "warning";
        }
        if (rejectedCount + ignoredCount > 0L) {
            return "info";
        }
        return "success";
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

    private void writeAudit(String action, String resourceType, UUID resourceId, String projectId, Map<String, Object> after) {
        contextClient.writeAuditEvent(action, resourceType, resourceId.toString(), projectId, "SUCCEEDED", after);
    }

}
