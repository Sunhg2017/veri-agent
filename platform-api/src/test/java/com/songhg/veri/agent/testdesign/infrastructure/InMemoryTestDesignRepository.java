package com.songhg.veri.agent.testdesign.infrastructure;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCandidateQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCalibrationRunQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignConflictOperationQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignEvaluationSampleQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTemplateQuery;
import com.songhg.veri.agent.testdesign.domain.TestDesignAuditChainAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCalibrationRun;
import com.songhg.veri.agent.testdesign.domain.TestDesignCalibrationSummary;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignConflictOperationRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignConflictOperationSummary;
import com.songhg.veri.agent.testdesign.domain.TestDesignContextPolicyNote;
import com.songhg.veri.agent.testdesign.domain.TestDesignContextPolicyOverride;
import com.songhg.veri.agent.testdesign.domain.TestDesignCrossWpOperationsAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignEvaluationSample;
import com.songhg.veri.agent.testdesign.domain.TestDesignEvaluationSampleSummary;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignReleaseReadinessApproval;
import com.songhg.veri.agent.testdesign.domain.TestDesignReleaseReadinessNote;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportManifest;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignTemplate;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Profile("local")
@Primary
@Repository
public class InMemoryTestDesignRepository implements TestDesignRepository {

    private static final String ACTION_AUTO_COMPENSATE_LINK_EXISTING = "AUTO_COMPENSATE_LINK_EXISTING";
    private static final String ACTION_DUPLICATE_REVIEW_REQUIRED = "DUPLICATE_REVIEW_REQUIRED";

    private final ConcurrentHashMap<UUID, TestDesignTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDesignCandidate> candidates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDesignReviewRecord> reviewRecords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDesignPublishRecord> publishRecords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDesignReportManifest> reportManifests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDesignContextPolicyOverride> contextPolicyOverrides = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDesignContextPolicyNote> contextPolicyNotes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDesignReleaseReadinessApproval> releaseReadinessApprovals = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDesignReleaseReadinessNote> releaseReadinessNotes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDesignTemplate> templates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDesignEvaluationSample> evaluationSamples = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDesignCalibrationRun> calibrationRuns = new ConcurrentHashMap<>();

    @Override
    public List<TestDesignTemplate> templates(TestDesignTemplateQuery query) {
        return filteredTemplates(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countTemplates(TestDesignTemplateQuery query) {
        return filteredTemplates(query).count();
    }

    @Override
    public Optional<TestDesignTemplate> template(UUID id) {
        return Optional.ofNullable(templates.get(id));
    }

    @Override
    public Optional<TestDesignTemplate> templateByScopeAndName(String projectId, String name) {
        if (!StringUtils.hasText(name)) {
            return Optional.empty();
        }
        return templates.values().stream()
                .filter(template -> sameNullableProject(projectId, template.projectId()))
                .filter(template -> name.trim().equalsIgnoreCase(template.name()))
                .findFirst();
    }

    @Override
    public TestDesignTemplate saveTemplate(TestDesignTemplate template) {
        templates.put(template.id(), template);
        return template;
    }

    @Override
    public List<TestDesignTask> tasks(TestDesignTaskQuery query) {
        return filteredTasks(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countTasks(TestDesignTaskQuery query) {
        return filteredTasks(query).count();
    }

    @Override
    public long countTasksByStatus(TestDesignTaskStatus status) {
        if (status == null) {
            return 0L;
        }
        return tasks.values().stream()
                .filter(task -> status.name().equals(task.status()))
                .count();
    }

    @Override
    public Optional<Instant> oldestTaskUpdatedAtByStatus(TestDesignTaskStatus status) {
        if (status == null) {
            return Optional.empty();
        }
        return tasks.values().stream()
                .filter(task -> status.name().equals(task.status()))
                .map(InMemoryTestDesignRepository::lastTouchedAt)
                .min(Comparator.naturalOrder());
    }

    @Override
    public long countStaleRunningTasks(Instant staleBefore) {
        if (staleBefore == null) {
            return 0L;
        }
        return tasks.values().stream()
                .filter(task -> TestDesignTaskStatus.RUNNING.name().equals(task.status()))
                .filter(task -> lastTouchedAt(task).isBefore(staleBefore))
                .count();
    }

    @Override
    public long countCandidatesByStatus(TestDesignCandidateStatus status) {
        if (status == null) {
            return 0L;
        }
        return candidates.values().stream()
                .filter(candidate -> status.name().equals(candidate.status()))
                .count();
    }

    @Override
    public Optional<Instant> oldestCandidateUpdatedAtByStatus(TestDesignCandidateStatus status) {
        if (status == null) {
            return Optional.empty();
        }
        return candidates.values().stream()
                .filter(candidate -> status.name().equals(candidate.status()))
                .map(TestDesignCandidate::updatedAt)
                .min(Comparator.naturalOrder());
    }

    @Override
    public Optional<TestDesignTask> task(UUID id) {
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public Optional<TestDesignTask> taskByIdempotencyKey(String projectId, String idempotencyKey) {
        if (!StringUtils.hasText(projectId) || !StringUtils.hasText(idempotencyKey)) {
            return Optional.empty();
        }
        return tasks.values().stream()
                .filter(task -> projectId.equals(task.projectId()))
                .filter(task -> idempotencyKey.equals(task.idempotencyKey()))
                .findFirst();
    }

    @Override
    public TestDesignTask saveTask(TestDesignTask task) {
        tasks.put(task.id(), task);
        return task;
    }

    @Override
    public boolean markTaskStatus(
            UUID id,
            TestDesignTaskStatus expectedStatus,
            TestDesignTaskStatus nextStatus,
            Instant updatedAt
    ) {
        synchronized (tasks) {
            TestDesignTask current = tasks.get(id);
            if (current == null || !expectedStatus.name().equals(current.status())) {
                return false;
            }
            tasks.put(id, new TestDesignTask(
                    current.id(),
                    current.projectId(),
                    current.title(),
                    nextStatus.name(),
                    current.requirementIds(),
                    current.coverageTypes(),
                    current.promptKey(),
                    current.promptVersion(),
                    current.modelInvocationId(),
                    current.modelProviderName(),
                    current.modelName(),
                    current.totalRequirements(),
                    current.generatedCount(),
                    current.confirmedCount(),
                    current.publishedCount(),
                    null,
                    current.requestedBy(),
                    current.idempotencyKey(),
                    current.requestDigest(),
                    current.inputDigest(),
                    current.contextSummaryJson(),
                    current.createdAt(),
                    updatedAt
            ));
            return true;
        }
    }

    @Override
    public boolean markCandidateStatus(
            UUID id,
            TestDesignCandidateStatus expectedStatus,
            TestDesignCandidateStatus nextStatus,
            Instant updatedAt
    ) {
        synchronized (candidates) {
            TestDesignCandidate current = candidates.get(id);
            if (current == null || !expectedStatus.name().equals(current.status())) {
                return false;
            }
            candidates.put(id, new TestDesignCandidate(
                    current.id(),
                    current.taskId(),
                    current.projectId(),
                    current.requirementId(),
                    current.apiId(),
                    current.title(),
                    current.description(),
                    current.coverageType(),
                    current.priority(),
                    nextStatus.name(),
                    current.preconditions(),
                    current.stepsJson(),
                    current.expectedResult(),
                    current.tags(),
                    current.duplicateKey(),
                    current.confidence(),
                    current.promptKey(),
                    current.promptVersion(),
                    current.modelInvocationId(),
                    current.modelProviderName(),
                    current.modelName(),
                    current.assetCaseId(),
                    current.reviewComment(),
                    current.rejectedReason(),
                    current.ignoredReason(),
                    null,
                    current.confirmedBy(),
                    current.confirmedAt(),
                    current.version() + 1,
                    current.createdAt(),
                    updatedAt
            ));
            return true;
        }
    }

    @Override
    public int markStaleRunningTasksFailed(Instant failedAt, Instant staleBefore, String errorMessage, int limit) {
        if (limit <= 0) {
            return 0;
        }
        synchronized (tasks) {
            int affected = 0;
            List<TestDesignTask> staleTasks = tasks.values().stream()
                    .filter(current -> TestDesignTaskStatus.RUNNING.name().equals(current.status()))
                    .filter(current -> {
                        return lastTouchedAt(current).isBefore(staleBefore);
                    })
                    .sorted(Comparator.comparing(InMemoryTestDesignRepository::lastTouchedAt))
                    .limit(limit)
                    .toList();
            for (TestDesignTask current : staleTasks) {
                if (!TestDesignTaskStatus.RUNNING.name().equals(current.status())
                        || !lastTouchedAt(current).isBefore(staleBefore)) {
                    continue;
                }
                tasks.put(current.id(), new TestDesignTask(
                        current.id(),
                        current.projectId(),
                        current.title(),
                        TestDesignTaskStatus.FAILED.name(),
                        current.requirementIds(),
                        current.coverageTypes(),
                        current.promptKey(),
                        current.promptVersion(),
                        current.modelInvocationId(),
                        current.modelProviderName(),
                        current.modelName(),
                        current.totalRequirements(),
                        current.generatedCount(),
                        current.confirmedCount(),
                        current.publishedCount(),
                        errorMessage,
                        current.requestedBy(),
                        current.idempotencyKey(),
                        current.requestDigest(),
                        current.inputDigest(),
                        current.contextSummaryJson(),
                        current.createdAt(),
                        failedAt
                ));
                affected++;
            }
            return affected;
        }
    }

    @Override
    public List<TestDesignCandidate> candidates(TestDesignCandidateQuery query) {
        return filteredCandidates(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countCandidates(TestDesignCandidateQuery query) {
        return filteredCandidates(query).count();
    }

    @Override
    public List<TestDesignCandidate> candidatesByTask(UUID taskId) {
        return candidates.values().stream()
                .filter(candidate -> taskId.equals(candidate.taskId()))
                .sorted(Comparator.comparing(TestDesignCandidate::createdAt))
                .toList();
    }

    @Override
    public List<TestDesignCandidate> publishQueuedCandidates(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return candidates.values().stream()
                .filter(candidate -> TestDesignCandidateStatus.PUBLISH_QUEUED.name().equals(candidate.status()))
                .sorted(Comparator.comparing(TestDesignCandidate::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit)
                .toList();
    }

    @Override
    public int markStalePublishingCandidatesFailed(
            Instant failedAt,
            Instant staleBefore,
            String errorMessage,
            int limit
    ) {
        if (limit <= 0 || staleBefore == null) {
            return 0;
        }
        synchronized (candidates) {
            List<TestDesignCandidate> staleCandidates = candidates.values().stream()
                    .filter(candidate -> TestDesignCandidateStatus.PUBLISHING.name().equals(candidate.status()))
                    .filter(candidate -> candidate.updatedAt() != null && candidate.updatedAt().isBefore(staleBefore))
                    .sorted(Comparator.comparing(TestDesignCandidate::updatedAt))
                    .limit(limit)
                    .toList();
            for (TestDesignCandidate current : staleCandidates) {
                candidates.put(current.id(), new TestDesignCandidate(
                        current.id(),
                        current.taskId(),
                        current.projectId(),
                        current.requirementId(),
                        current.apiId(),
                        current.title(),
                        current.description(),
                        current.coverageType(),
                        current.priority(),
                        TestDesignCandidateStatus.FAILED.name(),
                        current.preconditions(),
                        current.stepsJson(),
                        current.expectedResult(),
                        current.tags(),
                        current.duplicateKey(),
                        current.confidence(),
                        current.promptKey(),
                        current.promptVersion(),
                        current.modelInvocationId(),
                        current.modelProviderName(),
                        current.modelName(),
                        current.assetCaseId(),
                        current.reviewComment(),
                        current.rejectedReason(),
                        current.ignoredReason(),
                        errorMessage,
                        current.confirmedBy(),
                        current.confirmedAt(),
                        current.version() + 1,
                        current.createdAt(),
                        failedAt
                ));
            }
            return staleCandidates.size();
        }
    }

    @Override
    public long countStalePublishingCandidates(Instant staleBefore) {
        if (staleBefore == null) {
            return 0L;
        }
        return candidates.values().stream()
                .filter(candidate -> TestDesignCandidateStatus.PUBLISHING.name().equals(candidate.status()))
                .filter(candidate -> candidate.updatedAt() != null && candidate.updatedAt().isBefore(staleBefore))
                .count();
    }

    @Override
    public List<TestDesignCandidate> publishCompensationCandidates(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return candidates.values().stream()
                .filter(candidate -> "FAILED".equals(candidate.status()))
                .filter(candidate -> candidate.assetCaseId() != null)
                .filter(candidate -> publishRecords.values().stream()
                        .noneMatch(record -> candidate.id().equals(record.candidateId())
                                && "SUCCEEDED".equals(record.result())))
                .filter(candidate -> publishRecords.values().stream()
                        .noneMatch(record -> candidate.id().equals(record.candidateId())
                                && "AUTO_COMPENSATE_LINK_EXISTING".equals(record.action())))
                .sorted(Comparator.comparing(TestDesignCandidate::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<TestDesignCandidate> candidate(UUID id) {
        return Optional.ofNullable(candidates.get(id));
    }

    @Override
    public TestDesignCandidate saveCandidate(TestDesignCandidate candidate) {
        candidates.put(candidate.id(), candidate);
        return candidate;
    }

    @Override
    public TestDesignReviewRecord saveReviewRecord(TestDesignReviewRecord record) {
        reviewRecords.put(record.id(), record);
        return record;
    }

    @Override
    public List<TestDesignReviewRecord> reviewRecords(UUID taskId, PageQuery pageQuery) {
        return filteredReviewRecords(taskId)
                .skip(pageQuery.offset())
                .limit(pageQuery.size())
                .toList();
    }

    @Override
    public List<TestDesignReviewRecord> reviewRecordsByTask(UUID taskId) {
        return filteredReviewRecords(taskId).toList();
    }

    @Override
    public long countReviewRecords(UUID taskId) {
        return filteredReviewRecords(taskId).count();
    }

    @Override
    public TestDesignPublishRecord savePublishRecord(TestDesignPublishRecord record) {
        if (ACTION_AUTO_COMPENSATE_LINK_EXISTING.equals(record.action())
                && publishRecords.values().stream()
                        .anyMatch(existing -> record.candidateId().equals(existing.candidateId())
                                && ACTION_AUTO_COMPENSATE_LINK_EXISTING.equals(existing.action()))) {
            throw new IllegalStateException("Duplicate automatic publish compensation record for candidate: "
                    + record.candidateId());
        }
        publishRecords.put(record.id(), record);
        return record;
    }

    @Override
    public List<TestDesignPublishRecord> publishRecords(UUID taskId) {
        return publishRecords.values().stream()
                .filter(record -> taskId.equals(record.taskId()))
                .sorted(Comparator.comparing(TestDesignPublishRecord::createdAt).reversed())
                .toList();
    }

    @Override
    public List<TestDesignConflictOperationRecord> conflictOperations(TestDesignConflictOperationQuery query) {
        return filteredConflictOperations(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countConflictOperations(TestDesignConflictOperationQuery query) {
        return filteredConflictOperations(query).count();
    }

    @Override
    public TestDesignConflictOperationSummary conflictOperationSummary(TestDesignConflictOperationQuery query) {
        List<TestDesignConflictOperationRecord> records = filteredConflictOperations(query).toList();
        long openCount = records.stream().filter(record -> !record.resolved()).count();
        long duplicateReviewCount = records.stream()
                .filter(record -> ACTION_DUPLICATE_REVIEW_REQUIRED.equals(record.action()))
                .count();
        Instant latestConflictAt = records.stream()
                .map(TestDesignConflictOperationRecord::recordCreatedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new TestDesignConflictOperationSummary(
                records.size(),
                openCount,
                records.size() - openCount,
                duplicateReviewCount,
                latestConflictAt
        );
    }

    @Override
    public TestDesignReportManifest saveReportManifest(TestDesignReportManifest manifest) {
        Optional<TestDesignReportManifest> existing = reportManifests.values().stream()
                .filter(current -> manifest.taskId().equals(current.taskId()))
                .filter(current -> manifest.schemaVersion().equals(current.schemaVersion()))
                .filter(current -> manifest.fieldSetVersion().equals(current.fieldSetVersion()))
                .filter(current -> manifest.contentDigest().equals(current.contentDigest()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        reportManifests.put(manifest.id(), manifest);
        return manifest;
    }

    @Override
    public List<TestDesignReportManifest> reportManifestsByTask(UUID taskId) {
        return reportManifests.values().stream()
                .filter(manifest -> taskId.equals(manifest.taskId()))
                .sorted(Comparator.comparing(TestDesignReportManifest::createdAt).reversed())
                .toList();
    }

    @Override
    public TestDesignAuditChainAggregate auditChainAggregate(UUID taskId) {
        TestDesignTask task = tasks.get(taskId);
        if (task == null) {
            return emptyAuditChainAggregate();
        }
        List<TestDesignCandidate> taskCandidates = candidatesByTask(taskId);
        List<TestDesignPublishRecord> taskPublishRecords = publishRecords(taskId);
        long domainWriteCount = 1L + reviewRecordsByTask(taskId).size();
        long reportExportCount = reportManifestsByTask(taskId).size();
        long publishedCaseCount = taskCandidates.stream()
                .filter(candidate -> candidate.assetCaseId() != null)
                .count();
        long traceLinkCount = taskPublishRecords.stream()
                .filter(record -> !record.dryRun())
                .filter(record -> "SUCCEEDED".equals(record.result()))
                .filter(record -> record.assetCaseId() != null)
                .count();
        long modelInvocationCount = task.modelInvocationId() == null ? 0L : 1L;
        return new TestDesignAuditChainAggregate(
                domainWriteCount + reportExportCount,
                domainWriteCount + reportExportCount,
                0,
                0,
                domainWriteCount,
                0,
                0,
                reportExportCount,
                modelInvocationCount,
                modelInvocationCount,
                0,
                0,
                0,
                0,
                0,
                0,
                "0",
                modelInvocationCount,
                modelInvocationCount,
                publishedCaseCount,
                traceLinkCount,
                0,
                0,
                0
        );
    }

    @Override
    public TestDesignCrossWpOperationsAggregate crossWpOperationsAggregate(String projectId, String promptKey) {
        List<TestDesignTask> scopedTasks = tasks.values().stream()
                .filter(task -> matches(projectId, task.projectId()))
                .filter(task -> matches(promptKey, task.promptKey()))
                .toList();
        List<UUID> taskIds = scopedTasks.stream().map(TestDesignTask::id).toList();
        List<TestDesignCandidate> scopedCandidates = candidates.values().stream()
                .filter(candidate -> taskIds.contains(candidate.taskId()))
                .toList();
        List<TestDesignPublishRecord> scopedPublishRecords = publishRecords.values().stream()
                .filter(record -> taskIds.contains(record.taskId()))
                .toList();
        long candidateScopeMismatchCount = scopedCandidates.stream()
                .filter(candidate -> tasks.get(candidate.taskId()) != null)
                .filter(candidate -> !tasks.get(candidate.taskId()).projectId().equals(candidate.projectId()))
                .count();
        long publishScopeMismatchCount = scopedPublishRecords.stream()
                .filter(record -> tasks.get(record.taskId()) != null)
                .filter(record -> !tasks.get(record.taskId()).projectId().equals(record.projectId()))
                .count();
        long modelInvocationReferenceCount = scopedTasks.stream().filter(task -> task.modelInvocationId() != null).count()
                + scopedCandidates.stream().filter(candidate -> candidate.modelInvocationId() != null).count();
        long publishProjectScopeRecordCount = scopedPublishRecords.stream()
                .filter(record -> StringUtils.hasText(record.projectId()))
                .count();
        long reviewRecordCount = reviewRecords.values().stream()
                .filter(record -> taskIds.contains(record.taskId()))
                .count();
        long reportManifestCount = reportManifests.values().stream()
                .filter(manifest -> taskIds.contains(manifest.taskId()))
                .count();
        long publishedCaseCount = scopedCandidates.stream()
                .filter(candidate -> candidate.assetCaseId() != null)
                .count();
        long traceLinkCount = scopedPublishRecords.stream()
                .filter(record -> !record.dryRun())
                .filter(record -> "SUCCEEDED".equals(record.result()))
                .filter(record -> record.assetCaseId() != null)
                .count();
        long wp1AuditEventCount = scopedTasks.size() + reviewRecordCount + reportManifestCount;
        return new TestDesignCrossWpOperationsAggregate(
                scopedTasks.size(),
                scopedCandidates.size(),
                scopedPublishRecords.size(),
                scopedTasks.stream().map(TestDesignTask::projectId).filter(StringUtils::hasText).distinct().count(),
                candidateScopeMismatchCount,
                publishScopeMismatchCount,
                modelInvocationReferenceCount,
                publishProjectScopeRecordCount,
                wp1AuditEventCount,
                wp1AuditEventCount,
                0,
                0,
                modelInvocationReferenceCount,
                modelInvocationReferenceCount,
                0,
                0,
                0,
                modelInvocationReferenceCount,
                publishedCaseCount,
                traceLinkCount,
                0,
                0,
                0,
                0,
                0,
                0
        );
    }

    @Override
    public int requeueAuditOutbox(
            String projectId,
            String status,
            int limit,
            String reason,
            String actor,
            Instant now
    ) {
        return 0;
    }

    @Override
    public TestDesignContextPolicyOverride saveContextPolicyOverride(TestDesignContextPolicyOverride override) {
        contextPolicyOverrides.put(override.id(), override);
        return override;
    }

    @Override
    public TestDesignContextPolicyNote saveContextPolicyNote(TestDesignContextPolicyNote note) {
        contextPolicyNotes.put(note.id(), note);
        return note;
    }

    @Override
    public Optional<TestDesignContextPolicyOverride> contextPolicyOverride(UUID id) {
        return Optional.ofNullable(contextPolicyOverrides.get(id));
    }

    @Override
    public List<TestDesignContextPolicyOverride> contextPolicyOverrides(String projectId, String environmentKey) {
        return contextPolicyOverrides.values().stream()
                .filter(override -> projectId.equals(override.projectId()))
                .filter(override -> {
                    if (StringUtils.hasText(environmentKey)) {
                        return !StringUtils.hasText(override.environmentKey())
                                || environmentKey.equals(override.environmentKey());
                    }
                    return !StringUtils.hasText(override.environmentKey());
                })
                .sorted(Comparator.comparing(TestDesignContextPolicyOverride::createdAt).reversed())
                .toList();
    }

    @Override
    public List<TestDesignContextPolicyNote> contextPolicyNotes(UUID overrideId) {
        return contextPolicyNotes.values().stream()
                .filter(note -> overrideId.equals(note.overrideId()))
                .sorted(Comparator.comparing(TestDesignContextPolicyNote::createdAt))
                .toList();
    }

    @Override
    public Optional<TestDesignContextPolicyOverride> latestApprovedProjectContextPolicyOverride(String projectId) {
        return latestApprovedContextPolicyOverride(projectId, null);
    }

    @Override
    public Optional<TestDesignContextPolicyOverride> latestApprovedEnvironmentContextPolicyOverride(
            String projectId,
            String environmentKey
    ) {
        return latestApprovedContextPolicyOverride(projectId, environmentKey);
    }

    private Optional<TestDesignContextPolicyOverride> latestApprovedContextPolicyOverride(
            String projectId,
            String environmentKey
    ) {
        return contextPolicyOverrides.values().stream()
                .filter(override -> "APPROVED".equals(override.status()))
                .filter(override -> projectId.equals(override.projectId()))
                .filter(override -> {
                    if (StringUtils.hasText(environmentKey)) {
                        return environmentKey.equals(override.environmentKey());
                    }
                    return !StringUtils.hasText(override.environmentKey());
                })
                .max(Comparator.comparing(TestDesignContextPolicyOverride::updatedAt));
    }

    @Override
    public TestDesignReleaseReadinessApproval saveReleaseReadinessApproval(TestDesignReleaseReadinessApproval approval) {
        releaseReadinessApprovals.put(approval.id(), approval);
        return approval;
    }

    @Override
    public TestDesignReleaseReadinessNote saveReleaseReadinessNote(TestDesignReleaseReadinessNote note) {
        releaseReadinessNotes.put(note.id(), note);
        return note;
    }

    @Override
    public Optional<TestDesignReleaseReadinessApproval> releaseReadinessApproval(UUID id) {
        return Optional.ofNullable(releaseReadinessApprovals.get(id));
    }

    @Override
    public List<TestDesignReleaseReadinessApproval> releaseReadinessApprovals(UUID taskId) {
        return releaseReadinessApprovals.values().stream()
                .filter(approval -> taskId.equals(approval.taskId()))
                .sorted(Comparator.comparing(TestDesignReleaseReadinessApproval::createdAt).reversed())
                .toList();
    }

    @Override
    public List<TestDesignReleaseReadinessNote> releaseReadinessNotes(UUID approvalId) {
        return releaseReadinessNotes.values().stream()
                .filter(note -> approvalId.equals(note.approvalId()))
                .sorted(Comparator.comparing(TestDesignReleaseReadinessNote::createdAt))
                .toList();
    }

    @Override
    public Optional<TestDesignReleaseReadinessApproval> latestApprovedReleaseReadinessApproval(UUID taskId) {
        return releaseReadinessApprovals.values().stream()
                .filter(approval -> taskId.equals(approval.taskId()))
                .filter(approval -> "APPROVED".equals(approval.status()))
                .max(Comparator.comparing(TestDesignReleaseReadinessApproval::updatedAt));
    }

    @Override
    public List<TestDesignEvaluationSample> evaluationSamples(TestDesignEvaluationSampleQuery query) {
        return filteredEvaluationSamples(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countEvaluationSamples(TestDesignEvaluationSampleQuery query) {
        return filteredEvaluationSamples(query).count();
    }

    @Override
    public Optional<TestDesignEvaluationSample> evaluationSample(UUID id) {
        return Optional.ofNullable(evaluationSamples.get(id));
    }

    @Override
    public Optional<TestDesignEvaluationSample> evaluationSampleByProjectAndKey(String projectId, String sampleKey) {
        if (!StringUtils.hasText(projectId) || !StringUtils.hasText(sampleKey)) {
            return Optional.empty();
        }
        return evaluationSamples.values().stream()
                .filter(sample -> projectId.equals(sample.projectId()))
                .filter(sample -> sampleKey.equalsIgnoreCase(sample.sampleKey()))
                .findFirst();
    }

    @Override
    public TestDesignEvaluationSample saveEvaluationSample(TestDesignEvaluationSample sample) {
        evaluationSamples.put(sample.id(), sample);
        return sample;
    }

    @Override
    public TestDesignEvaluationSampleSummary evaluationSampleSummary(String projectId, String promptKey) {
        List<TestDesignEvaluationSample> samples = evaluationSamples.values().stream()
                .filter(sample -> matches(projectId, sample.projectId()))
                .filter(sample -> matches(promptKey, sample.promptKey()))
                .toList();
        return sampleSummary(samples);
    }

    @Override
    public List<TestDesignCalibrationRun> calibrationRuns(TestDesignCalibrationRunQuery query) {
        return filteredCalibrationRuns(query)
                .skip(query.offset())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countCalibrationRuns(TestDesignCalibrationRunQuery query) {
        return filteredCalibrationRuns(query).count();
    }

    @Override
    public TestDesignCalibrationRun saveCalibrationRun(TestDesignCalibrationRun run) {
        calibrationRuns.put(run.id(), run);
        return run;
    }

    @Override
    public TestDesignCalibrationSummary calibrationSummary(String projectId, String promptKey) {
        List<TestDesignCalibrationRun> runs = calibrationRuns.values().stream()
                .filter(run -> matches(projectId, run.projectId()))
                .filter(run -> matches(promptKey, run.promptKey()))
                .toList();
        return calibrationSummary(runs);
    }

    @Override
    public Optional<TestDesignCalibrationRun> latestCalibrationRun(
            String projectId,
            String promptKey,
            String promptVersion
    ) {
        return calibrationRuns.values().stream()
                .filter(run -> matches(projectId, run.projectId()))
                .filter(run -> matches(promptKey, run.promptKey()))
                .filter(run -> matches(promptVersion, run.promptVersion()))
                .max(Comparator.comparing(TestDesignCalibrationRun::createdAt));
    }

    private Stream<TestDesignTask> filteredTasks(TestDesignTaskQuery query) {
        return tasks.values().stream()
                .filter(task -> matches(query.projectId(), task.projectId()))
                .filter(task -> matches(query.status(), task.status()))
                .filter(task -> matches(query.promptKey(), task.promptKey()))
                .filter(task -> contains(query.keyword(), task.title(), task.requirementIds()))
                .sorted(Comparator.comparing(TestDesignTask::createdAt).reversed());
    }

    private Stream<TestDesignTemplate> filteredTemplates(TestDesignTemplateQuery query) {
        return templates.values().stream()
                .filter(template -> {
                    if (!StringUtils.hasText(query.projectId())) {
                        return true;
                    }
                    if (query.includeGlobal() && !StringUtils.hasText(template.projectId())) {
                        return true;
                    }
                    return query.projectId().equals(template.projectId());
                })
                .filter(template -> query.enabled() == null || query.enabled() == template.enabled())
                .filter(template -> contains(query.keyword(), template.name(), template.description(), template.promptKey()))
                .sorted(Comparator
                        .comparing((TestDesignTemplate template) -> StringUtils.hasText(template.projectId()) ? 0 : 1)
                        .thenComparing(TestDesignTemplate::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(TestDesignTemplate::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private Stream<TestDesignCandidate> filteredCandidates(TestDesignCandidateQuery query) {
        return candidates.values().stream()
                .filter(candidate -> query.taskId() == null || query.taskId().equals(candidate.taskId()))
                .filter(candidate -> matches(query.projectId(), candidate.projectId()))
                .filter(candidate -> query.requirementId() == null || query.requirementId().equals(candidate.requirementId()))
                .filter(candidate -> matches(query.status(), candidate.status()))
                .filter(candidate -> matches(query.coverageType(), candidate.coverageType()))
                .filter(candidate -> contains(query.keyword(), candidate.title(), candidate.description(), candidate.tags()))
                .sorted(Comparator.comparing(TestDesignCandidate::createdAt).reversed());
    }

    private Stream<TestDesignReviewRecord> filteredReviewRecords(UUID taskId) {
        return reviewRecords.values().stream()
                .filter(record -> taskId.equals(record.taskId()))
                .sorted(Comparator.comparing(TestDesignReviewRecord::createdAt).reversed());
    }

    private Stream<TestDesignConflictOperationRecord> filteredConflictOperations(TestDesignConflictOperationQuery query) {
        return publishRecords.values().stream()
                .filter(record -> !record.dryRun())
                .filter(InMemoryTestDesignRepository::isConflictOperationSignal)
                .filter(record -> matches(query.projectId(), record.projectId()))
                .filter(record -> query.taskId() == null || query.taskId().equals(record.taskId()))
                .map(this::toConflictOperationRecord)
                .flatMap(Optional::stream)
                .filter(record -> matches(query.action(), record.action()))
                .filter(record -> matches(query.result(), record.result()))
                .filter(record -> matches(query.candidateStatus(), record.candidateStatus()))
                .filter(record -> contains(
                        query.keyword(),
                        record.candidateTitle(),
                        record.taskTitle(),
                        record.errorMessage(),
                        record.action(),
                        record.result(),
                        record.candidateId().toString(),
                        record.assetCaseId() == null ? null : record.assetCaseId().toString()
                ))
                .filter(record -> matchesResolutionStatus(query.resolutionStatus(), record.resolved()))
                .sorted(Comparator.comparing(TestDesignConflictOperationRecord::recordCreatedAt).reversed()
                        .thenComparing(record -> record.publishRecordId().toString(), Comparator.reverseOrder()));
    }

    private Stream<TestDesignEvaluationSample> filteredEvaluationSamples(TestDesignEvaluationSampleQuery query) {
        return evaluationSamples.values().stream()
                .filter(sample -> matches(query.projectId(), sample.projectId()))
                .filter(sample -> matches(query.promptKey(), sample.promptKey()))
                .filter(sample -> matches(query.promptVersion(), sample.promptVersion()))
                .filter(sample -> matches(query.status(), sample.status()))
                .filter(sample -> matches(query.coverageType(), sample.coverageType()))
                .filter(sample -> matches(query.baselineVersion(), sample.baselineVersion()))
                .filter(sample -> contains(
                        query.keyword(),
                        sample.sampleKey(),
                        sample.title(),
                        sample.tags(),
                        sample.maintenanceNote()
                ))
                .sorted(Comparator.comparing(TestDesignEvaluationSample::updatedAt).reversed()
                        .thenComparing(sample -> sample.id().toString(), Comparator.reverseOrder()));
    }

    private Stream<TestDesignCalibrationRun> filteredCalibrationRuns(TestDesignCalibrationRunQuery query) {
        return calibrationRuns.values().stream()
                .filter(run -> matches(query.projectId(), run.projectId()))
                .filter(run -> matches(query.promptKey(), run.promptKey()))
                .filter(run -> matches(query.promptVersion(), run.promptVersion()))
                .filter(run -> matches(query.baselineVersion(), run.baselineVersion()))
                .filter(run -> matches(query.status(), run.status()))
                .sorted(Comparator.comparing(TestDesignCalibrationRun::createdAt).reversed()
                        .thenComparing(run -> run.id().toString(), Comparator.reverseOrder()));
    }

    private Optional<TestDesignConflictOperationRecord> toConflictOperationRecord(TestDesignPublishRecord record) {
        TestDesignCandidate candidate = candidates.get(record.candidateId());
        TestDesignTask task = tasks.get(record.taskId());
        if (candidate == null || task == null) {
            return Optional.empty();
        }
        boolean resolved = TestDesignCandidateStatus.PUBLISHED.name().equals(candidate.status())
                || publishRecords.values().stream()
                        .filter(existing -> record.candidateId().equals(existing.candidateId()))
                        .filter(existing -> !existing.dryRun())
                        .filter(existing -> "SUCCEEDED".equals(existing.result()))
                        .anyMatch(existing -> existing.createdAt().isAfter(record.createdAt()));
        return Optional.of(new TestDesignConflictOperationRecord(
                record.id(),
                record.taskId(),
                record.candidateId(),
                record.projectId(),
                record.requirementId(),
                record.assetCaseId(),
                record.dryRun(),
                record.action(),
                record.result(),
                record.errorMessage(),
                record.publishedBy(),
                record.createdAt(),
                task.title(),
                task.status(),
                candidate.title(),
                candidate.status(),
                candidate.version(),
                candidate.assetCaseId(),
                resolved
        ));
    }

    private static boolean isConflictOperationSignal(TestDesignPublishRecord record) {
        return ACTION_DUPLICATE_REVIEW_REQUIRED.equals(record.action())
                || "CONFLICT".equals(record.result())
                || ACTION_DUPLICATE_REVIEW_REQUIRED.equals(record.result());
    }

    private static boolean matchesResolutionStatus(String expected, boolean resolved) {
        if (!StringUtils.hasText(expected) || "ALL".equalsIgnoreCase(expected)) {
            return true;
        }
        if ("OPEN".equalsIgnoreCase(expected)) {
            return !resolved;
        }
        if ("RESOLVED".equalsIgnoreCase(expected)) {
            return resolved;
        }
        return true;
    }

    private static boolean matches(String expected, String actual) {
        return !StringUtils.hasText(expected) || expected.equalsIgnoreCase(actual);
    }

    private static boolean sameNullableProject(String expected, String actual) {
        if (!StringUtils.hasText(expected) && !StringUtils.hasText(actual)) {
            return true;
        }
        return StringUtils.hasText(expected) && expected.equals(actual);
    }

    private static Instant lastTouchedAt(TestDesignTask task) {
        return task.updatedAt() == null ? task.createdAt() : task.updatedAt();
    }

    private static boolean contains(String keyword, String... values) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase(java.util.Locale.ROOT);
        for (String value : values) {
            if (value != null && value.toLowerCase(java.util.Locale.ROOT).contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static TestDesignEvaluationSampleSummary sampleSummary(List<TestDesignEvaluationSample> samples) {
        long candidateCount = samples.stream().filter(sample -> "CANDIDATE".equals(sample.status())).count();
        long goldenCount = samples.stream().filter(sample -> "GOLDEN".equals(sample.status())).count();
        long frozenCount = samples.stream().filter(sample -> "FROZEN".equals(sample.status())).count();
        long deprecatedCount = samples.stream().filter(sample -> "DEPRECATED".equals(sample.status())).count();
        long baselineVersionCount = samples.stream()
                .map(TestDesignEvaluationSample::baselineVersion)
                .filter(StringUtils::hasText)
                .distinct()
                .count();
        Instant latestUpdatedAt = samples.stream()
                .map(TestDesignEvaluationSample::updatedAt)
                .filter(updatedAt -> updatedAt != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new TestDesignEvaluationSampleSummary(
                samples.size(),
                candidateCount,
                goldenCount,
                frozenCount,
                deprecatedCount,
                baselineVersionCount,
                latestUpdatedAt
        );
    }

    private static TestDesignCalibrationSummary calibrationSummary(List<TestDesignCalibrationRun> runs) {
        long passedCount = runs.stream().filter(run -> "PASSED".equals(run.status())).count();
        long warningCount = runs.stream().filter(run -> "WARNING".equals(run.status())).count();
        long blockedCount = runs.stream().filter(run -> "BLOCKED".equals(run.status())).count();
        TestDesignCalibrationRun latest = runs.stream()
                .max(Comparator.comparing(TestDesignCalibrationRun::createdAt))
                .orElse(null);
        return new TestDesignCalibrationSummary(
                runs.size(),
                passedCount,
                warningCount,
                blockedCount,
                latest == null ? null : latest.status(),
                latest == null ? null : latest.createdAt()
        );
    }

    private static TestDesignAuditChainAggregate emptyAuditChainAggregate() {
        return new TestDesignAuditChainAggregate(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                "0", 0, 0, 0, 0, 0, 0, 0
        );
    }
}
