package com.songhg.veri.agent.testdata.application;

import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.testdata.application.view.TestDataWorkerTickResponse;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.songhg.veri.agent.testdata.domain.TestDataTask;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Service;

@Service
public class TestDataWorkerService implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(TestDataWorkerService.class);
    private static final int MAX_ERROR_SUMMARY_LENGTH = 512;

    private final TestDataTaskService taskService;
    private final TestAccountLeaseService leaseService;
    private final TestAccountHealthCheckService accountHealthCheckService;
    private final TestDataProperties properties;

    public TestDataWorkerService(
            TestDataTaskService taskService,
            TestAccountLeaseService leaseService,
            TestAccountHealthCheckService accountHealthCheckService,
            TestDataProperties properties
    ) {
        this.taskService = taskService;
        this.leaseService = leaseService;
        this.accountHealthCheckService = accountHealthCheckService;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(new FixedDelayTask(
                this::runBySchedule,
                Duration.ofMillis(scheduledFixedDelayMillis()),
                Duration.ofMillis(scheduledInitialDelayMillis())
        ));
    }

    public void runBySchedule() {
        if (!properties.workerEnabled()) {
            return;
        }
        String traceId = TraceContext.createTraceId();
        try (TraceContext.TraceScope ignored = TraceContext.open(traceId)) {
            runOnce();
        } catch (RuntimeException exception) {
            log.warn(
                    "WP8 test-data worker tick failed, traceId={}, error={}",
                    traceId,
                    SensitiveTextSanitizer.sanitizedErrorSummary(
                            exception.getMessage(),
                            "WP8 test-data worker failed",
                            MAX_ERROR_SUMMARY_LENGTH
                    )
            );
            log.debug("WP8 test-data worker failure details", exception);
        }
    }

    public long scheduledFixedDelayMillis() {
        return properties.effectiveWorkerIntervalMs();
    }

    public long scheduledInitialDelayMillis() {
        return properties.effectiveWorkerInitialDelayMs();
    }

    /**
     * Runs one bounded managed tick through lease recovery, account drift reconciliation, and pending task execution.
     *
     * <p>The method is synchronized so manual invocations and the scheduled loop do not overlap inside one JVM.
     * Cross-JVM safety still relies on the underlying row-level or conditional update guards in the WP8 services.</p>
     */
    public synchronized TestDataWorkerTickResponse runOnce() {
        String traceId = TraceContext.getOrCreateTraceId();
        Instant tickedAt = Instant.now();
        String workerId = properties.effectiveWorkerId();
        int taskBatchSize = properties.effectiveWorkerTaskBatchSize();
        int leaseRecoveryBatchSize = properties.effectiveLeaseRecoveryBatchSize();
        int accountHealthCheckBatchSize = properties.effectiveAccountHealthCheckBatchSize();
        if (!properties.workerEnabled()) {
            return new TestDataWorkerTickResponse(
                    false,
                    workerId,
                    taskBatchSize,
                    leaseRecoveryBatchSize,
                    accountHealthCheckBatchSize,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    true,
                    traceId,
                    tickedAt
            );
        }

        int recoveredExpiredLeaseCount = leaseService.expireActiveLeases(tickedAt, leaseRecoveryBatchSize);
        TestAccountHealthCheckService.AccountHealthCheckResult healthCheckResult =
                accountHealthCheckService.runManagedChecks(tickedAt, accountHealthCheckBatchSize, workerId);
        int claimedTaskCount = 0;
        int succeededTaskCount = 0;
        int failedTaskCount = 0;
        int skippedTaskCount = 0;
        for (TestDataTask queued : taskService.pendingTasks(taskBatchSize)) {
            Optional<String> outcome = taskService.processPendingTask(queued.id(), workerId);
            if (outcome.isEmpty()) {
                skippedTaskCount++;
                continue;
            }
            claimedTaskCount++;
            if ("SUCCEEDED".equals(outcome.get())) {
                succeededTaskCount++;
            } else if ("FAILED".equals(outcome.get())) {
                failedTaskCount++;
            }
        }
        boolean noop = recoveredExpiredLeaseCount == 0
                && claimedTaskCount == 0
                && healthCheckResult.updatedAccountCount() == 0
                && skippedTaskCount == 0;
        return new TestDataWorkerTickResponse(
                true,
                workerId,
                taskBatchSize,
                leaseRecoveryBatchSize,
                accountHealthCheckBatchSize,
                recoveredExpiredLeaseCount,
                claimedTaskCount,
                succeededTaskCount,
                failedTaskCount,
                skippedTaskCount,
                healthCheckResult.scannedAccountCount(),
                healthCheckResult.updatedAccountCount(),
                healthCheckResult.lockedAccountCount(),
                healthCheckResult.leasedAccountCount(),
                noop,
                traceId,
                tickedAt
        );
    }
}
