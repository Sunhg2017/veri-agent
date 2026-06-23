package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.notification.application.AsyncTaskNotificationService;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskQuery;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class TestDesignEventRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(TestDesignEventRecoveryService.class);

    private final TestDesignRepository repository;
    private final TestDesignEventPublisher eventPublisher;
    private final TestDesignProperties properties;
    private final AsyncTaskNotificationService notificationService;
    @Autowired
    public TestDesignEventRecoveryService(
            TestDesignRepository repository,
            TestDesignEventPublisher eventPublisher,
            TestDesignProperties properties,
            AsyncTaskNotificationService notificationService
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.notificationService = notificationService;
    }

    TestDesignEventRecoveryService(
            TestDesignRepository repository,
            TestDesignEventPublisher eventPublisher,
            TestDesignProperties properties,
            AsyncTaskNotificationService notificationService,
            String ignoredRecoveryCron
    ) {
        this(repository, eventPublisher, properties, notificationService);
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
     * Re-emits queued task events without changing task state; the consumer owns generation through a conditional claim.
     */
    public RecoveryResult recoverQueuedEvents(String trigger) {
        if (!properties.eventRecoveryEnabled()) {
            return new RecoveryResult(trigger, 0, 0, 0, 0, 0, 0, false, false);
        }
        Instant checkedAt = Instant.now();
        RuntimeSignals runtimeSignals = runtimeSignals(checkedAt);
        int timedOutRunningTasks = failTimedOutRunningTasks();
        List<TestDesignTask> queuedTasks = repository.tasks(new TestDesignTaskQuery(
                null,
                TestDesignTaskStatus.QUEUED.name(),
                null,
                null,
                PageQuery.of(0, recoveryBatchSize())
        ));
        queuedTasks.forEach(task -> eventPublisher.publishGenerationRequested(task.id()));
        if (!queuedTasks.isEmpty() || timedOutRunningTasks > 0 || runtimeSignals.hasWarning()) {
            log.info(
                    "WP5 test design generation recovery completed, trigger={}, queuedTasks={}, timedOutRunningTasks={}, "
                            + "queuedTaskCount={}, runningTaskCount={}, oldestQueuedAgeSeconds={}, "
                            + "staleRunningTaskCount={}, queueLagWarning={}, timeoutWarning={}",
                    trigger,
                    queuedTasks.size(),
                    timedOutRunningTasks,
                    runtimeSignals.queuedTaskCount(),
                    runtimeSignals.runningTaskCount(),
                    runtimeSignals.oldestQueuedAgeSeconds(),
                    runtimeSignals.staleRunningTaskCount(),
                    runtimeSignals.queueLagWarning(),
                    runtimeSignals.timeoutWarning()
            );
        }
        return new RecoveryResult(
                trigger,
                queuedTasks.size(),
                timedOutRunningTasks,
                runtimeSignals.queuedTaskCount(),
                runtimeSignals.runningTaskCount(),
                runtimeSignals.oldestQueuedAgeSeconds(),
                runtimeSignals.staleRunningTaskCount(),
                runtimeSignals.queueLagWarning(),
                runtimeSignals.timeoutWarning()
        );
    }

    private void recoverSafely(String trigger) {
        try {
            recoverQueuedEvents(trigger);
        } catch (RuntimeException exception) {
            log.warn("WP5 test design generation recovery skipped, trigger={}, message={}",
                    trigger, exception.getMessage());
        }
    }

    private int recoveryBatchSize() {
        return TestDesignGenerationOrchestrationPolicy.recoveryBatchSize(properties);
    }

    private int failTimedOutRunningTasks() {
        long timeoutSeconds = TestDesignGenerationOrchestrationPolicy.runningTimeoutSeconds(properties);
        if (timeoutSeconds <= 0) {
            return 0;
        }
        Instant failedAt = Instant.now();
        Instant staleBefore = failedAt.minusSeconds(timeoutSeconds);
        List<TestDesignTask> staleRunningTasks = repository.tasks(new TestDesignTaskQuery(
                null,
                TestDesignTaskStatus.RUNNING.name(),
                null,
                null,
                PageQuery.of(0, recoveryBatchSize())
        )).stream()
                .filter(task -> {
                    Instant updatedAt = task.updatedAt() != null ? task.updatedAt() : task.createdAt();
                    return updatedAt != null && updatedAt.isBefore(staleBefore);
                })
                .toList();
        // Stale RUNNING tasks are failed instead of re-emitted so retries remain explicit and idempotent.
        int affected = repository.markStaleRunningTasksFailed(
                failedAt,
                staleBefore,
                "生成任务运行超时，已由恢复扫描标记失败，可重试",
                recoveryBatchSize()
        );
        staleRunningTasks.stream()
                .limit(affected)
                .map(TestDesignTask::id)
                .map(repository::task)
                .flatMap(java.util.Optional::stream)
                .filter(task -> TestDesignTaskStatus.FAILED.name().equals(task.status()))
                .forEach(notificationService::notifyTestDesignGenerationFailed);
        return affected;
    }

    private RuntimeSignals runtimeSignals(Instant checkedAt) {
        long queueLagWarningSeconds = TestDesignGenerationOrchestrationPolicy.queueLagWarningSeconds(properties);
        long runningTimeoutSeconds = TestDesignGenerationOrchestrationPolicy.runningTimeoutSeconds(properties);
        long queuedTaskCount = repository.countTasksByStatus(TestDesignTaskStatus.QUEUED);
        long runningTaskCount = repository.countTasksByStatus(TestDesignTaskStatus.RUNNING);
        long oldestQueuedAgeSeconds = repository.oldestTaskUpdatedAtByStatus(TestDesignTaskStatus.QUEUED)
                .map(updatedAt -> TestDesignGenerationOrchestrationPolicy.ageSeconds(checkedAt, updatedAt))
                .orElse(0L);
        long staleRunningTaskCount = runningTimeoutSeconds <= 0L
                ? 0L
                : repository.countStaleRunningTasks(checkedAt.minusSeconds(runningTimeoutSeconds));
        return new RuntimeSignals(
                queuedTaskCount,
                runningTaskCount,
                oldestQueuedAgeSeconds,
                staleRunningTaskCount,
                queueLagWarningSeconds > 0L && oldestQueuedAgeSeconds >= queueLagWarningSeconds,
                runningTimeoutSeconds > 0L && staleRunningTaskCount > 0L
        );
    }

    public record RecoveryResult(
            String trigger,
            int queuedTasks,
            int timedOutRunningTasks,
            long queuedTaskCount,
            long runningTaskCount,
            long oldestQueuedAgeSeconds,
            long staleRunningTaskCount,
            boolean queueLagWarning,
            boolean timeoutWarning
    ) {
    }

    private record RuntimeSignals(
            long queuedTaskCount,
            long runningTaskCount,
            long oldestQueuedAgeSeconds,
            long staleRunningTaskCount,
            boolean queueLagWarning,
            boolean timeoutWarning
    ) {
        boolean hasWarning() {
            return queueLagWarning || timeoutWarning;
        }
    }
}
