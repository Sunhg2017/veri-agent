package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.execution.application.command.CompleteExecutionNodeRunCommand;
import com.songhg.veri.agent.execution.application.command.DispatchExecutionNodeRunCommand;
import com.songhg.veri.agent.execution.application.view.ExecutionCronScanResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionQueueClaimResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionQueueRecoveryResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionSchedulerTickResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ExecutionSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionSchedulerService.class);
    private static final int MAX_ERROR_SUMMARY_LENGTH = 512;

    private final ExecutionRunService executionRunService;
    private final ExecutionTriggerService executionTriggerService;
    private final ExecutionProperties properties;
    private final ExecutionSchedulerLock schedulerLock;

    @Autowired
    public ExecutionSchedulerService(
            ExecutionRunService executionRunService,
            ExecutionTriggerService executionTriggerService,
            ExecutionProperties properties,
            ExecutionSchedulerLock schedulerLock
    ) {
        this.executionRunService = executionRunService;
        this.executionTriggerService = executionTriggerService;
        this.properties = properties;
        this.schedulerLock = schedulerLock;
    }

    public ExecutionSchedulerService(
            ExecutionRunService executionRunService,
            ExecutionTriggerService executionTriggerService,
            ExecutionProperties properties
    ) {
        this(executionRunService, executionTriggerService, properties, new LocalExecutionSchedulerLock());
    }

    /**
     * Keeps the legacy manual entry point so tests and ad-hoc maintenance can still reuse the safe wrapper.
     */
    public void runBySchedule() {
        if (!properties.schedulerEnabled()) {
            return;
        }
        String traceId = TraceContext.createTraceId();
        try (TraceContext.TraceScope ignored = TraceContext.open(traceId)) {
            runOnce();
        } catch (RuntimeException exception) {
            log.warn(
                    "WP9 scheduler tick failed, traceId={}, error={}",
                    traceId,
                    sanitizedSummary(exception.getMessage())
            );
            log.debug("WP9 scheduler tick failure details", exception);
        }
    }

    public long scheduledFixedDelayMillis() {
        return properties.effectiveSchedulerIntervalMs();
    }

    public long scheduledInitialDelayMillis() {
        return properties.effectiveSchedulerInitialDelayMs();
    }

    /**
     * Runs one managed scheduler tick through existing queue leases and dispatch contracts.
     *
     * <p>The scheduler first asks the trigger control plane to materialize due CRON events, then lets recovery release
     * expired claims, and finally claims bounded queued work. Runner dispatch still only goes through
     * `ExecutionRunService`, so trigger idempotency, active claim tokens, state transitions and summary redaction stay
     * centralized in their owning services. The method is synchronized so manual/admin-triggered ticks cannot overlap
     * with the managed loop inside the same JVM; database row locks still protect CRON scans across JVMs.</p>
     */
    public synchronized ExecutionSchedulerTickResponse runOnce() {
        String traceId = TraceContext.getOrCreateTraceId();
        Instant tickedAt = Instant.now();
        String workerId = properties.effectiveSchedulerWorkerId();
        int tickBatchSize = properties.effectiveSchedulerTickBatchSize();
        if (!properties.schedulerEnabled()) {
            return newTickResponse(
                    false,
                    workerId,
                    tickBatchSize,
                    null,
                    null,
                    0,
                    0,
                    0,
                    0,
                    false,
                    schedulerLock.provider(),
                    schedulerLock.distributed(),
                    "SCHEDULER_DISABLED",
                    traceId,
                    tickedAt
            );
        }

        ExecutionSchedulerLock.LockAttempt lockAttempt = acquireLeaderLock();
        if (!lockAttempt.acquired()) {
            return newTickResponse(
                    true,
                    workerId,
                    tickBatchSize,
                    null,
                    null,
                    0,
                    0,
                    0,
                    0,
                    false,
                    lockAttempt.provider(),
                    lockAttempt.distributed(),
                    lockAttempt.skipReason(),
                    traceId,
                    tickedAt
            );
        }
        try (lockAttempt) {
            ExecutionCronScanResponse cronScan = executionTriggerService.scanDueCronTriggers(tickBatchSize);
            ExecutionQueueRecoveryResponse recovery = executionRunService.recoverExpiredQueueClaims();
            int claimedNodeCount = 0;
            int dispatchedNodeCount = 0;
            int completedNodeCount = 0;
            int failedNodeCount = 0;
            for (int index = 0; index < tickBatchSize; index++) {
                Optional<ExecutionQueueClaimResponse> claim = executionRunService.claimNextQueuedNode(workerId);
                if (claim.isEmpty()) {
                    break;
                }
                claimedNodeCount++;
                ClaimOutcome outcome = handleClaim(claim.get());
                dispatchedNodeCount += outcome.dispatchedNodeCount();
                completedNodeCount += outcome.completedNodeCount();
                failedNodeCount += outcome.failedNodeCount();
            }
            return newTickResponse(
                    true,
                    workerId,
                    tickBatchSize,
                    cronScan,
                    recovery,
                    claimedNodeCount,
                    dispatchedNodeCount,
                    completedNodeCount,
                    failedNodeCount,
                    true,
                    lockAttempt.provider(),
                    lockAttempt.distributed(),
                    null,
                    traceId,
                    tickedAt
            );
        }
    }

    private ExecutionSchedulerLock.LockAttempt acquireLeaderLock() {
        if (!properties.schedulerLeaderLockEnabled()) {
            return ExecutionSchedulerLock.LockAttempt.acquired(
                    "DISABLED",
                    false,
                    () -> {
                    }
            );
        }
        return schedulerLock.tryAcquire(
                properties.effectiveSchedulerLeaderLockName(),
                Duration.ofMillis(properties.effectiveSchedulerLeaderLockWaitMs()),
                Duration.ofMillis(properties.effectiveSchedulerLeaderLockLeaseMs())
        );
    }

    private ClaimOutcome handleClaim(ExecutionQueueClaimResponse claim) {
        try {
            if ("WP6_API".equals(claim.runnerType())) {
                executionRunService.dispatchClaimedApiTestNodeRun(new DispatchExecutionNodeRunCommand(
                        claim.nodeRunId(),
                        claim.claimToken(),
                        null,
                        null,
                        null,
                        null,
                        null
                ));
                return new ClaimOutcome(1, 0, 0);
            }
            if ("WP7_UI".equals(claim.runnerType())) {
                executionRunService.dispatchClaimedUiTestNodeRun(new DispatchExecutionNodeRunCommand(
                        claim.nodeRunId(),
                        claim.claimToken(),
                        null,
                        null,
                        null,
                        null,
                        null
                ));
                return new ClaimOutcome(1, 0, 0);
            }
            if ("REPORT".equals(claim.runnerType())) {
                completeReportHandoff(claim);
                return new ClaimOutcome(0, 1, 0);
            }
            blockUnsupportedRunner(claim);
            return new ClaimOutcome(0, 0, 1);
        } catch (RuntimeException exception) {
            failClaimedNode(claim, exception);
            return new ClaimOutcome(0, 0, 1);
        }
    }

    private void completeReportHandoff(ExecutionQueueClaimResponse claim) {
        executionRunService.completeClaimedNodeRun(new CompleteExecutionNodeRunCommand(
                claim.nodeRunId(),
                claim.claimToken(),
                "SUCCEEDED",
                null,
                null,
                Map.of(
                        "schedulerManaged", true,
                        "reportHandoffReady", true,
                        "rawReportStored", false
                )
        ));
    }

    private void blockUnsupportedRunner(ExecutionQueueClaimResponse claim) {
        executionRunService.completeClaimedNodeRun(new CompleteExecutionNodeRunCommand(
                claim.nodeRunId(),
                claim.claimToken(),
                "BLOCKED",
                "EXECUTION_RUNNER_NOT_READY",
                "Runner type is not managed by WP9 scheduler",
                Map.of(
                        "schedulerManaged", true,
                        "unsupportedRunnerType", safeRunnerType(claim.runnerType())
                )
        ));
    }

    private void failClaimedNode(ExecutionQueueClaimResponse claim, RuntimeException exception) {
        try {
            executionRunService.completeClaimedNodeRun(new CompleteExecutionNodeRunCommand(
                    claim.nodeRunId(),
                    claim.claimToken(),
                    "FAILED",
                    "EXECUTION_NODE_DISPATCH_FAILED",
                    sanitizedSummary(exception.getMessage()),
                    Map.of(
                            "schedulerManaged", true,
                            "schedulerFailure", true,
                            "sourceErrorCode", sourceErrorCode(exception)
                    )
            ));
        } catch (RuntimeException completionException) {
            log.warn(
                    "WP9 scheduler failed to close claimed node, nodeRunId={}, runnerType={}, error={}",
                    claim.nodeRunId(),
                    safeRunnerType(claim.runnerType()),
                    sanitizedSummary(completionException.getMessage())
            );
            log.debug("WP9 scheduler claim close failure details", completionException);
        }
    }

    private ExecutionSchedulerTickResponse newTickResponse(
            boolean schedulerEnabled,
            String workerId,
            int tickBatchSize,
            ExecutionCronScanResponse cronScan,
            ExecutionQueueRecoveryResponse recovery,
            int claimedNodeCount,
            int dispatchedNodeCount,
            int completedNodeCount,
            int failedNodeCount,
            boolean leaderLockAcquired,
            String leaderLockProvider,
            boolean leaderLockDistributed,
            String skipReason,
            String traceId,
            Instant tickedAt
    ) {
        int recoveredExpiredClaimCount = recovery == null ? 0 : recovery.expiredClaimCount();
        int recoveredRequeuedNodeCount = recovery == null ? 0 : recovery.requeuedNodeCount();
        int recoveredTimedOutNodeCount = recovery == null ? 0 : recovery.timedOutNodeCount();
        int cronScannedTriggerCount = cronScan == null ? 0 : cronScan.scannedTriggerCount();
        int cronTriggeredRunCount = cronScan == null ? 0 : cronScan.triggeredRunCount();
        int cronFailedTriggerCount = cronScan == null ? 0 : cronScan.failedTriggerCount();
        boolean noop = recoveredExpiredClaimCount == 0
                && recoveredRequeuedNodeCount == 0
                && recoveredTimedOutNodeCount == 0
                && cronScannedTriggerCount == 0
                && claimedNodeCount == 0;
        return new ExecutionSchedulerTickResponse(
                schedulerEnabled,
                workerId,
                tickBatchSize,
                recoveredExpiredClaimCount,
                recoveredRequeuedNodeCount,
                recoveredTimedOutNodeCount,
                cronScannedTriggerCount,
                cronTriggeredRunCount,
                cronFailedTriggerCount,
                claimedNodeCount,
                dispatchedNodeCount,
                completedNodeCount,
                failedNodeCount,
                leaderLockAcquired,
                leaderLockProvider,
                leaderLockDistributed,
                skipReason,
                noop,
                traceId,
                tickedAt
        );
    }

    private String sourceErrorCode(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().name();
        }
        return exception.getClass().getSimpleName();
    }

    private String safeRunnerType(String runnerType) {
        if (!StringUtils.hasText(runnerType)) {
            return "UNKNOWN";
        }
        String normalized = runnerType.trim().replaceAll("[^A-Za-z0-9_.:-]", "_");
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private String sanitizedSummary(String value) {
        return SensitiveTextSanitizer.sanitizedErrorSummary(
                value,
                "Execution scheduler failed",
                MAX_ERROR_SUMMARY_LENGTH
        );
    }

    private record ClaimOutcome(int dispatchedNodeCount, int completedNodeCount, int failedNodeCount) {
    }
}
