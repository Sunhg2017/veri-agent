package com.songhg.veri.agent.testdesign.infrastructure;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignCandidateQuery;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskQuery;
import com.songhg.veri.agent.testdesign.domain.TestDesignAuditChainAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignContextPolicyOverride;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignReportManifest;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
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

    private final ConcurrentHashMap<UUID, TestDesignTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDesignCandidate> candidates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDesignReviewRecord> reviewRecords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDesignPublishRecord> publishRecords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDesignReportManifest> reportManifests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TestDesignContextPolicyOverride> contextPolicyOverrides = new ConcurrentHashMap<>();

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
    public TestDesignContextPolicyOverride saveContextPolicyOverride(TestDesignContextPolicyOverride override) {
        contextPolicyOverrides.put(override.id(), override);
        return override;
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

    private Stream<TestDesignTask> filteredTasks(TestDesignTaskQuery query) {
        return tasks.values().stream()
                .filter(task -> matches(query.projectId(), task.projectId()))
                .filter(task -> matches(query.status(), task.status()))
                .filter(task -> matches(query.promptKey(), task.promptKey()))
                .filter(task -> contains(query.keyword(), task.title(), task.requirementIds()))
                .sorted(Comparator.comparing(TestDesignTask::createdAt).reversed());
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

    private static boolean matches(String expected, String actual) {
        return !StringUtils.hasText(expected) || expected.equalsIgnoreCase(actual);
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

    private static TestDesignAuditChainAggregate emptyAuditChainAggregate() {
        return new TestDesignAuditChainAggregate(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                "0", 0, 0, 0, 0, 0, 0, 0
        );
    }
}
