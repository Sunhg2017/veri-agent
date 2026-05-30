package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskQuery;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TestDesignEventRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(TestDesignEventRecoveryService.class);

    private final TestDesignRepository repository;
    private final TestDesignEventPublisher eventPublisher;
    private final TestDesignProperties properties;
    private final String recoveryCron;

    public TestDesignEventRecoveryService(
            TestDesignRepository repository,
            TestDesignEventPublisher eventPublisher,
            TestDesignProperties properties,
            @Value("${veri-agent.test-design.event-recovery-cron:0 */2 * * * *}") String recoveryCron
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.recoveryCron = recoveryCron;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recoverSafely("startup");
    }

    @Scheduled(cron = "${veri-agent.test-design.event-recovery-cron:0 */2 * * * *}")
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
                            + "staleRunningTaskCount={}, queueLagWarning={}, timeoutWarning={}, cron={}",
                    trigger,
                    queuedTasks.size(),
                    timedOutRunningTasks,
                    runtimeSignals.queuedTaskCount(),
                    runtimeSignals.runningTaskCount(),
                    runtimeSignals.oldestQueuedAgeSeconds(),
                    runtimeSignals.staleRunningTaskCount(),
                    runtimeSignals.queueLagWarning(),
                    runtimeSignals.timeoutWarning(),
                    recoveryCron
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
        // Stale RUNNING tasks are failed instead of re-emitted so retries remain explicit and idempotent.
        return repository.markStaleRunningTasksFailed(
                failedAt,
                staleBefore,
                "生成任务运行超时，已由恢复扫描标记失败，可重试",
                recoveryBatchSize()
        );
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
