package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskQuery;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class TestDesignPublishEventRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(TestDesignPublishEventRecoveryService.class);

    private final TestDesignRepository repository;
    private final TestDesignEventPublisher eventPublisher;
    private final TestDesignProperties properties;
    @Autowired
    public TestDesignPublishEventRecoveryService(
            TestDesignRepository repository,
            TestDesignEventPublisher eventPublisher,
            TestDesignProperties properties
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    TestDesignPublishEventRecoveryService(
            TestDesignRepository repository,
            TestDesignEventPublisher eventPublisher,
            TestDesignProperties properties,
            String ignoredRecoveryCron
    ) {
        this(repository, eventPublisher, properties);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recoverSafely("startup");
    }

    /**
     * Keeps the legacy manual entry point so tests and ad-hoc maintenance can still reuse the safe wrapper.
     */
    public void recoverBySchedule() {
        recoverSafely("schedule");
    }

    /**
     * Re-emits publish events from durable candidate queue state.
     *
     * <p>The scan groups only PUBLISH_QUEUED candidates by task. It never queries CONFIRMED candidates, which keeps
     * explicit publish requests bounded even when reviewers confirm more candidates before recovery runs.
     */
    public PublishRecoveryResult recoverQueuedPublishes(String trigger) {
        if (!properties.publishEventRecoveryEnabled()) {
            return new PublishRecoveryResult(trigger, 0, 0, 0, 0, 0, 0, 0, false, false);
        }
        Instant checkedAt = Instant.now();
        PublishRuntimeSignals runtimeSignals = runtimeSignals(checkedAt);
        int timedOutPublishingCandidates = failTimedOutPublishingCandidates();
        int closedTransientTasks = closeCompletedTransientPublishTasks();
        Map<UUID, List<UUID>> queuedCandidatesByTask = repository.publishQueuedCandidates(recoveryBatchSize()).stream()
                .collect(Collectors.groupingBy(
                        TestDesignCandidate::taskId,
                        LinkedHashMap::new,
                        Collectors.mapping(TestDesignCandidate::id, Collectors.toList())
                ));
        queuedCandidatesByTask.forEach(eventPublisher::publishPublishRequested);
        if (!queuedCandidatesByTask.isEmpty() || timedOutPublishingCandidates > 0
                || closedTransientTasks > 0 || runtimeSignals.hasWarning()) {
            log.info(
                    "WP5 test design publish recovery completed, trigger={}, queuedTaskEvents={}, "
                            + "timedOutPublishingCandidates={}, closedTransientTasks={}, queuedCandidateCount={}, "
                            + "publishingCandidateCount={}, oldestQueuedAgeSeconds={}, stalePublishingCandidateCount={}, "
                            + "queueLagWarning={}, timeoutWarning={}",
                    trigger,
                    queuedCandidatesByTask.size(),
                    timedOutPublishingCandidates,
                    closedTransientTasks,
                    runtimeSignals.queuedCandidateCount(),
                    runtimeSignals.publishingCandidateCount(),
                    runtimeSignals.oldestQueuedAgeSeconds(),
                    runtimeSignals.stalePublishingCandidateCount(),
                    runtimeSignals.queueLagWarning(),
                    runtimeSignals.timeoutWarning()
            );
        }
        return new PublishRecoveryResult(
                trigger,
                queuedCandidatesByTask.size(),
                timedOutPublishingCandidates,
                closedTransientTasks,
                runtimeSignals.queuedCandidateCount(),
                runtimeSignals.publishingCandidateCount(),
                runtimeSignals.oldestQueuedAgeSeconds(),
                runtimeSignals.stalePublishingCandidateCount(),
                runtimeSignals.queueLagWarning(),
                runtimeSignals.timeoutWarning()
        );
    }

    private void recoverSafely(String trigger) {
        try {
            recoverQueuedPublishes(trigger);
        } catch (RuntimeException exception) {
            log.warn("WP5 test design publish recovery skipped, trigger={}, message={}",
                    trigger, exception.getMessage());
        }
    }

    private int failTimedOutPublishingCandidates() {
        long timeoutSeconds = runningTimeoutSeconds();
        if (timeoutSeconds <= 0L) {
            return 0;
        }
        Instant failedAt = Instant.now();
        return repository.markStalePublishingCandidatesFailed(
                failedAt,
                failedAt.minusSeconds(timeoutSeconds),
                "发布任务运行超时，已由恢复扫描标记失败，可重试",
                recoveryBatchSize()
        );
    }

    private int closeCompletedTransientPublishTasks() {
        int affected = 0;
        for (TestDesignTaskStatus status : List.of(TestDesignTaskStatus.PUBLISH_QUEUED, TestDesignTaskStatus.PUBLISHING)) {
            List<TestDesignTask> tasks = repository.tasks(new TestDesignTaskQuery(
                    null,
                    status.name(),
                    null,
                    null,
                    PageQuery.of(0, recoveryBatchSize())
            ));
            for (TestDesignTask task : tasks) {
                if (closeCompletedTransientPublishTask(task)) {
                    affected++;
                }
            }
        }
        return affected;
    }

    private boolean closeCompletedTransientPublishTask(TestDesignTask task) {
        List<TestDesignCandidate> candidates = repository.candidatesByTask(task.id());
        boolean hasInFlightCandidate = candidates.stream()
                .anyMatch(candidate -> TestDesignCandidateStatus.PUBLISH_QUEUED.name().equals(candidate.status())
                        || TestDesignCandidateStatus.PUBLISHING.name().equals(candidate.status()));
        if (hasInFlightCandidate) {
            return false;
        }
        /*
         * Recovery can fail a stale PUBLISHING candidate after the original consumer has died. The task-level
         * transient status must then be closed so reviewers can inspect the failure and explicitly retry it.
         */
        repository.saveTask(withTaskCounts(task, candidates));
        return true;
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
                : TestDesignTaskStatus.SUCCEEDED.name();
        return new TestDesignTask(
                task.id(), task.projectId(), task.title(), status, task.requirementIds(), task.coverageTypes(),
                task.promptKey(), task.promptVersion(), task.modelInvocationId(), task.modelProviderName(),
                task.modelName(), task.totalRequirements(), generatedCount, confirmedCount, publishedCount,
                task.errorMessage(), task.requestedBy(), task.idempotencyKey(), task.requestDigest(),
                task.inputDigest(), task.contextSummaryJson(), task.createdAt(), Instant.now()
        );
    }

    private PublishRuntimeSignals runtimeSignals(Instant checkedAt) {
        long queueLagWarningSeconds = queueLagWarningSeconds();
        long timeoutSeconds = runningTimeoutSeconds();
        long oldestQueuedAgeSeconds = repository.oldestCandidateUpdatedAtByStatus(TestDesignCandidateStatus.PUBLISH_QUEUED)
                .map(updatedAt -> ageSeconds(checkedAt, updatedAt))
                .orElse(0L);
        long stalePublishingCandidateCount = timeoutSeconds <= 0L
                ? 0L
                : repository.countStalePublishingCandidates(checkedAt.minusSeconds(timeoutSeconds));
        return new PublishRuntimeSignals(
                repository.countCandidatesByStatus(TestDesignCandidateStatus.PUBLISH_QUEUED),
                repository.countCandidatesByStatus(TestDesignCandidateStatus.PUBLISHING),
                oldestQueuedAgeSeconds,
                stalePublishingCandidateCount,
                queueLagWarningSeconds > 0L && oldestQueuedAgeSeconds >= queueLagWarningSeconds,
                timeoutSeconds > 0L && stalePublishingCandidateCount > 0L
        );
    }

    private int recoveryBatchSize() {
        return properties.effectivePublishEventRecoveryBatchSize();
    }

    private long runningTimeoutSeconds() {
        return Math.max(0L, properties.publishEventRecoveryRunningTimeoutSeconds());
    }

    private long queueLagWarningSeconds() {
        return Math.max(0L, properties.publishEventRecoveryQueueLagWarningSeconds());
    }

    private static long ageSeconds(Instant now, Instant timestamp) {
        if (now == null || timestamp == null || timestamp.isAfter(now)) {
            return 0L;
        }
        return Math.max(0L, Duration.between(timestamp, now).getSeconds());
    }

    public record PublishRecoveryResult(
            String trigger,
            int queuedTaskEvents,
            int timedOutPublishingCandidates,
            int closedTransientTasks,
            long queuedCandidateCount,
            long publishingCandidateCount,
            long oldestQueuedAgeSeconds,
            long stalePublishingCandidateCount,
            boolean queueLagWarning,
            boolean timeoutWarning
    ) {
    }

    private record PublishRuntimeSignals(
            long queuedCandidateCount,
            long publishingCandidateCount,
            long oldestQueuedAgeSeconds,
            long stalePublishingCandidateCount,
            boolean queueLagWarning,
            boolean timeoutWarning
    ) {
        boolean hasWarning() {
            return queueLagWarning || timeoutWarning;
        }
    }
}
