package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import com.songhg.veri.agent.testdesign.infrastructure.InMemoryTestDesignRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TestDesignEventRecoveryServiceTest {

    private final InMemoryTestDesignRepository repository = new InMemoryTestDesignRepository();
    private final TestDesignEventPublisher eventPublisher = mock(TestDesignEventPublisher.class);

    @Test
    void republishesQueuedTasksAndFailsStaleRunningTasks() {
        UUID queuedTaskId = UUID.randomUUID();
        UUID staleRunningTaskId = UUID.randomUUID();
        UUID freshRunningTaskId = UUID.randomUUID();
        repository.saveTask(task(queuedTaskId, TestDesignTaskStatus.QUEUED, Instant.now().minusSeconds(120)));
        repository.saveTask(task(staleRunningTaskId, TestDesignTaskStatus.RUNNING, Instant.now().minusSeconds(120)));
        repository.saveTask(task(freshRunningTaskId, TestDesignTaskStatus.RUNNING, Instant.now()));
        repository.saveTask(task(UUID.randomUUID(), TestDesignTaskStatus.SUCCEEDED, Instant.now().minusSeconds(120)));
        TestDesignEventRecoveryService service = new TestDesignEventRecoveryService(
                repository,
                eventPublisher,
                properties(true, 60),
                "0 */2 * * * *"
        );

        TestDesignEventRecoveryService.RecoveryResult result = service.recoverQueuedEvents("test");

        assertThat(result.trigger()).isEqualTo("test");
        assertThat(result.queuedTasks()).isEqualTo(1);
        assertThat(result.timedOutRunningTasks()).isEqualTo(1);
        assertThat(repository.task(queuedTaskId)).get().extracting(TestDesignTask::status)
                .isEqualTo(TestDesignTaskStatus.QUEUED.name());
        assertThat(repository.task(staleRunningTaskId)).get()
                .extracting(TestDesignTask::status, TestDesignTask::errorMessage)
                .containsExactly(TestDesignTaskStatus.FAILED.name(), "生成任务运行超时，已由恢复扫描标记失败，可重试");
        assertThat(repository.task(freshRunningTaskId)).get().extracting(TestDesignTask::status)
                .isEqualTo(TestDesignTaskStatus.RUNNING.name());
        verify(eventPublisher).publishGenerationRequested(queuedTaskId);
    }

    @Test
    void skipsRecoveryWhenDisabled() {
        UUID queuedTaskId = UUID.randomUUID();
        UUID staleRunningTaskId = UUID.randomUUID();
        repository.saveTask(task(queuedTaskId, TestDesignTaskStatus.QUEUED, Instant.now().minusSeconds(120)));
        repository.saveTask(task(staleRunningTaskId, TestDesignTaskStatus.RUNNING, Instant.now().minusSeconds(120)));
        TestDesignEventRecoveryService service = new TestDesignEventRecoveryService(
                repository,
                eventPublisher,
                properties(false, 60),
                "0 */2 * * * *"
        );

        TestDesignEventRecoveryService.RecoveryResult result = service.recoverQueuedEvents("disabled");

        assertThat(result.queuedTasks()).isZero();
        assertThat(result.timedOutRunningTasks()).isZero();
        assertThat(repository.task(queuedTaskId)).get().extracting(TestDesignTask::status)
                .isEqualTo(TestDesignTaskStatus.QUEUED.name());
        assertThat(repository.task(staleRunningTaskId)).get().extracting(TestDesignTask::status)
                .isEqualTo(TestDesignTaskStatus.RUNNING.name());
        verifyNoInteractions(eventPublisher);
    }

    private TestDesignTask task(UUID id, TestDesignTaskStatus status, Instant updatedAt) {
        return new TestDesignTask(
                id,
                "project-wp5",
                "WP5 recovery task",
                status.name(),
                UUID.randomUUID().toString(),
                "SMOKE",
                "wp5-test-design-v1",
                "1.0.0",
                null,
                null,
                null,
                1,
                0,
                0,
                0,
                null,
                "wp5-user",
                null,
                null,
                "digest-" + id,
                "{}",
                updatedAt.minusSeconds(10),
                updatedAt
        );
    }

    private TestDesignProperties properties(boolean recoveryEnabled, long runningTimeoutSeconds) {
        return new TestDesignProperties(
                "test-design-token",
                true,
                "RULE_TEMPLATE",
                "wp5-test-design-v1",
                "1.0.0",
                false,
                20,
                3,
                100,
                true,
                recoveryEnabled,
                20,
                runningTimeoutSeconds,
                100D,
                100D,
                20D,
                0D,
                0,
                0,
                0,
                0.86D,
                0.90D
        );
    }
}
