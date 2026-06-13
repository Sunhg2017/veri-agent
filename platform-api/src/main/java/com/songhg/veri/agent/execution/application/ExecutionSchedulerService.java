package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.execution.application.command.CompleteExecutionNodeRunCommand;
import com.songhg.veri.agent.execution.application.command.DispatchExecutionNodeRunCommand;
import com.songhg.veri.agent.execution.application.view.ExecutionQueueClaimResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionQueueRecoveryResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionSchedulerTickResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ExecutionSchedulerService implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ExecutionSchedulerService.class);
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s,;，；]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET_REF_PATTERN = Pattern.compile("secret://[^\\s,;，；]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENSITIVE_TEXT_PATTERN = Pattern.compile(
            "(?i)\\b(api[_-]?key|secret|token|password|passwd|authorization)\\s*[:=]\\s*[^\\s,;，；]+"
    );
    private static final int MAX_ERROR_SUMMARY_LENGTH = 512;

    private final ExecutionRunService executionRunService;
    private final ExecutionProperties properties;

    public ExecutionSchedulerService(ExecutionRunService executionRunService, ExecutionProperties properties) {
        this.executionRunService = executionRunService;
        this.properties = properties;
    }

    /**
     * Registers the managed loop with bounded configuration values instead of raw environment placeholders.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(new FixedDelayTask(
                this::runBySchedule,
                Duration.ofMillis(scheduledFixedDelayMillis()),
                Duration.ofMillis(scheduledInitialDelayMillis())
        ));
    }

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
     * <p>The scheduler never calls runner adapters directly. It first lets recovery release expired claims, then claims
     * bounded queued work and dispatches only through `ExecutionRunService`, so active claim tokens, state transitions
     * and summary redaction stay centralized in the execution control plane.</p>
     */
    public ExecutionSchedulerTickResponse runOnce() {
        String traceId = TraceContext.getOrCreateTraceId();
        Instant tickedAt = Instant.now();
        String workerId = properties.effectiveSchedulerWorkerId();
        int tickBatchSize = properties.effectiveSchedulerTickBatchSize();
        if (!properties.schedulerEnabled()) {
            return newTickResponse(false, workerId, tickBatchSize, null, 0, 0, 0, 0, traceId, tickedAt);
        }

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
                recovery,
                claimedNodeCount,
                dispatchedNodeCount,
                completedNodeCount,
                failedNodeCount,
                traceId,
                tickedAt
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
            ExecutionQueueRecoveryResponse recovery,
            int claimedNodeCount,
            int dispatchedNodeCount,
            int completedNodeCount,
            int failedNodeCount,
            String traceId,
            Instant tickedAt
    ) {
        int recoveredExpiredClaimCount = recovery == null ? 0 : recovery.expiredClaimCount();
        int recoveredRequeuedNodeCount = recovery == null ? 0 : recovery.requeuedNodeCount();
        int recoveredTimedOutNodeCount = recovery == null ? 0 : recovery.timedOutNodeCount();
        boolean noop = recoveredExpiredClaimCount == 0
                && recoveredRequeuedNodeCount == 0
                && recoveredTimedOutNodeCount == 0
                && claimedNodeCount == 0;
        return new ExecutionSchedulerTickResponse(
                schedulerEnabled,
                workerId,
                tickBatchSize,
                recoveredExpiredClaimCount,
                recoveredRequeuedNodeCount,
                recoveredTimedOutNodeCount,
                claimedNodeCount,
                dispatchedNodeCount,
                completedNodeCount,
                failedNodeCount,
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
        String summary = StringUtils.hasText(value) ? value.trim() : "Execution scheduler failed";
        summary = URL_PATTERN.matcher(summary).replaceAll("[REDACTED_URL]");
        summary = SECRET_REF_PATTERN.matcher(summary).replaceAll("[REDACTED_SECRET_REF]");
        summary = SENSITIVE_TEXT_PATTERN.matcher(summary).replaceAll("[REDACTED]");
        if (summary.length() <= MAX_ERROR_SUMMARY_LENGTH) {
            return summary;
        }
        return summary.substring(0, MAX_ERROR_SUMMARY_LENGTH - 3) + "...";
    }

    private record ClaimOutcome(int dispatchedNodeCount, int completedNodeCount, int failedNodeCount) {
    }
}
