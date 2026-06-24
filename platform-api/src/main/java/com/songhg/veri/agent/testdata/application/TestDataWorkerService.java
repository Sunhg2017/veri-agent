package com.songhg.veri.agent.testdata.application;

import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.testdata.application.view.TestDataWorkerTickResponse;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.songhg.veri.agent.testdata.domain.TestDataTask;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TestDataWorkerService {

    private static final Logger log = LoggerFactory.getLogger(TestDataWorkerService.class);
    private static final int MAX_ERROR_SUMMARY_LENGTH = 512;

    private final TestDataTaskService taskService;
    private final TestAccountLeaseService leaseService;
    private final TestAccountPoolService poolService;
    private final TestAccountHealthCheckService accountHealthCheckService;
    private final TestDataProperties properties;

    @Autowired
    public TestDataWorkerService(
            TestDataTaskService taskService,
            TestAccountLeaseService leaseService,
            TestAccountPoolService poolService,
            TestAccountHealthCheckService accountHealthCheckService,
            TestDataProperties properties
    ) {
        this.taskService = taskService;
        this.leaseService = leaseService;
        this.poolService = poolService;
        this.accountHealthCheckService = accountHealthCheckService;
        this.properties = properties;
    }

    public TestDataWorkerService(
            TestDataTaskService taskService,
            TestAccountLeaseService leaseService,
            TestAccountHealthCheckService accountHealthCheckService,
            TestDataProperties properties
    ) {
        this(taskService, leaseService, null, accountHealthCheckService, properties);
    }

    /**
     * Keeps the legacy manual entry point so tests and ad-hoc maintenance can still reuse the safe wrapper.
     */
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
        int accountProvisioningBatchSize = properties.effectiveAccountProvisioningBatchSize();
        if (!properties.workerEnabled()) {
            return new TestDataWorkerTickResponse(
                    false,
                    workerId,
                    taskBatchSize,
                    leaseRecoveryBatchSize,
                    accountHealthCheckBatchSize,
                    accountProvisioningBatchSize,
                    0,
                    0,
                    0,
                    0,
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
        TestAccountPoolService.AccountProvisioningTickResult provisioningResult = poolService == null
                ? new TestAccountPoolService.AccountProvisioningTickResult(
                        properties.accountProvisioningEnabled(),
                        false,
                        "DISABLED",
                        accountProvisioningBatchSize,
                        0,
                        0,
                        0,
                        0
                )
                : poolService.provisionAccounts(tickedAt, accountProvisioningBatchSize, workerId);
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
                && provisioningResult.provisionedAccountCount() == 0
                && provisioningResult.failedProvisioningCount() == 0
                && healthCheckResult.updatedAccountCount() == 0
                && skippedTaskCount == 0;
        return new TestDataWorkerTickResponse(
                true,
                workerId,
                taskBatchSize,
                leaseRecoveryBatchSize,
                accountHealthCheckBatchSize,
                accountProvisioningBatchSize,
                recoveredExpiredLeaseCount,
                claimedTaskCount,
                succeededTaskCount,
                failedTaskCount,
                skippedTaskCount,
                healthCheckResult.scannedAccountCount(),
                healthCheckResult.updatedAccountCount(),
                healthCheckResult.lockedAccountCount(),
                healthCheckResult.leasedAccountCount(),
                provisioningResult.scannedPoolCount(),
                provisioningResult.provisionedAccountCount(),
                provisioningResult.skippedPoolCount(),
                provisioningResult.failedProvisioningCount(),
                noop,
                traceId,
                tickedAt
        );
    }
}
