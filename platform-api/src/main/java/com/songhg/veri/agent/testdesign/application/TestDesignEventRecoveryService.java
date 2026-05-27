package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTaskQuery;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
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

    private static final int MAX_RECOVERY_BATCH_SIZE = 100;
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
            return new RecoveryResult(trigger, 0);
        }
        List<TestDesignTask> queuedTasks = repository.tasks(new TestDesignTaskQuery(
                null,
                TestDesignTaskStatus.QUEUED.name(),
                null,
                PageQuery.of(0, recoveryBatchSize())
        ));
        queuedTasks.forEach(task -> eventPublisher.publishGenerationRequested(task.id()));
        if (!queuedTasks.isEmpty()) {
            log.info("WP5 test design generation recovery published queued tasks, trigger={}, tasks={}, cron={}",
                    trigger, queuedTasks.size(), recoveryCron);
        }
        return new RecoveryResult(trigger, queuedTasks.size());
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
        int configured = properties.eventRecoveryBatchSize();
        return Math.max(1, Math.min(MAX_RECOVERY_BATCH_SIZE, configured <= 0 ? MAX_RECOVERY_BATCH_SIZE : configured));
    }

    public record RecoveryResult(String trigger, int tasks) {
    }
}
