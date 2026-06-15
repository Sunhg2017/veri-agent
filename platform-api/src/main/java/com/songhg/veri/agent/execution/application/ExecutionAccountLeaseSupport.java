package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.testdata.application.command.AcquireExecutionAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.ReleaseExecutionAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.view.TestDataCrossWpAccountSummary;
import com.songhg.veri.agent.testdata.application.view.TestDataExecutionAccountLeaseResponse;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * Coordinates WP9 execution runs with the WP8 account lease application contract.
 */
final class ExecutionAccountLeaseSupport {

    private static final Set<String> TERMINAL_RUN_STATUSES = Set.of(
            "SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "CANCELED", "TIMEOUT"
    );
    private static final Set<String> ACTIVE_LEASE_STATUSES = Set.of("ACTIVE");
    private static final int MAX_ROLE_TAGS = 20;
    private static final int MAX_TTL_SECONDS = 604_800;

    private final ExecutionRepository repository;
    private final TestDataCrossWpReferenceService testDataService;
    private final ExecutionProperties properties;
    private final ExecutionRunJsonSupport jsonSupport;

    ExecutionAccountLeaseSupport(
            ExecutionRepository repository,
            TestDataCrossWpReferenceService testDataService,
            ExecutionProperties properties,
            ExecutionRunJsonSupport jsonSupport
    ) {
        this.repository = repository;
        this.testDataService = testDataService;
        this.properties = properties;
        this.jsonSupport = jsonSupport;
    }

    /**
     * Acquires the plan-declared account lease before runner dispatch and persists only the WP8 safe summary.
     *
     * <p>The request key includes run, plan node and attempt identity so queue retry replays the same lease while a
     * control-plane retry gets a fresh key. WP9 never passes the lease into WP6 and never stores credential plaintext.</p>
     */
    ExecutionNodeRun acquireForDispatch(
            ExecutionRun run,
            ExecutionPlanNode planNode,
            ExecutionNodeRun nodeRun,
            Map<String, Object> planInput
    ) {
        AccountLeaseInput leaseInput = accountLeaseInput(planInput);
        if (leaseInput == null) {
            return nodeRun;
        }
        Map<String, Object> existingSummary = jsonSupport.readMap(nodeRun.resultSummaryJson());
        if (existingActiveLease(existingSummary)) {
            return nodeRun;
        }
        if (testDataService == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_ACCOUNT_LEASE_ADAPTER_UNAVAILABLE");
        }
        String requestKey = requestKey(run, planNode, nodeRun, leaseInput);
        TestDataExecutionAccountLeaseResponse lease = testDataService.acquireExecutionRunLease(
                new AcquireExecutionAccountLeaseCommand(
                        run.projectId(),
                        leaseInput.applicationId(),
                        leaseInput.environmentId(),
                        leaseInput.accountPoolRef(),
                        leaseInput.roleTags(),
                        run.id().toString(),
                        leaseInput.ttlSeconds() == null
                                ? defaultLeaseTtlSeconds(planNode)
                                : leaseInput.ttlSeconds(),
                        requestKey
                )
        );
        Instant now = Instant.now();
        ExecutionNodeRun updated = new ExecutionNodeRun(
                nodeRun.id(),
                nodeRun.runId(),
                nodeRun.planNodeId(),
                nodeRun.status(),
                nodeRun.attempt(),
                nodeRun.runnerType(),
                nodeRun.externalRunId(),
                nodeRun.errorCode(),
                nodeRun.errorSummary(),
                jsonSupport.mergedSummary(nodeRun.resultSummaryJson(), acquisitionSummary(lease, leaseInput, requestKey, now)),
                nodeRun.heartbeatAt(),
                nodeRun.queuedAt(),
                nodeRun.startedAt(),
                nodeRun.finishedAt(),
                nodeRun.createdAt(),
                now
        );
        if (!repository.updateNodeRunIfStatus(updated, "RUNNING")) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_NOT_ACTIVE");
        }
        return updated;
    }

    /**
     * Best-effort releases all active WP8 leases once the WP9 run reaches a terminal state.
     *
     * <p>Release failures are kept as sanitized evidence on the node and run summaries. The execution run remains
     * terminal because WP8 remains the lease state-machine owner and operators can retry by accountLeaseRef/run ref.</p>
     */
    ExecutionRun releaseTerminalRunLeases(ExecutionRun run, List<ExecutionNodeRun> nodeRuns, Instant now) {
        if (run == null || !TERMINAL_RUN_STATUSES.contains(run.status()) || nodeRuns == null || nodeRuns.isEmpty()) {
            return run;
        }
        List<ExecutionNodeRun> updatedNodes = new ArrayList<>();
        int leaseRefCount = 0;
        int releaseAttemptCount = 0;
        int releaseSuccessCount = 0;
        int releaseFailedCount = 0;
        for (ExecutionNodeRun nodeRun : nodeRuns) {
            Map<String, Object> summary = jsonSupport.readMap(nodeRun.resultSummaryJson());
            UUID leaseRef = accountLeaseRef(summary);
            if (leaseRef == null) {
                continue;
            }
            leaseRefCount++;
            if (alreadyReleased(summary)) {
                continue;
            }
            releaseAttemptCount++;
            ReleaseAttempt attempt = releaseLease(run, leaseRef);
            if (attempt.success()) {
                releaseSuccessCount++;
            } else {
                releaseFailedCount++;
            }
            updatedNodes.add(nodeWithReleaseSummary(nodeRun, attempt, now));
        }
        if (!updatedNodes.isEmpty()) {
            repository.updateNodeRuns(updatedNodes);
        }
        if (leaseRefCount == 0) {
            return run;
        }
        Map<String, Object> releaseSummary = new LinkedHashMap<>();
        releaseSummary.put("accountLeaseReleaseReady", true);
        releaseSummary.put("accountLeaseRefCount", leaseRefCount);
        releaseSummary.put("accountLeaseReleaseAttemptCount", releaseAttemptCount);
        releaseSummary.put("accountLeaseReleaseSuccessCount", releaseSuccessCount);
        releaseSummary.put("accountLeaseReleaseFailedCount", releaseFailedCount);
        releaseSummary.put("accountLeaseReleaseAccountStatus", releaseAccountStatus(run.status()));
        releaseSummary.put("accountLeaseReleaseCompletedAt", now.toString());
        ExecutionRun updated = new ExecutionRun(
                run.id(),
                run.planId(),
                run.projectId(),
                run.status(),
                run.triggerType(),
                run.requestKey(),
                run.sourceEventId(),
                run.attempt(),
                run.traceId(),
                jsonSupport.mergedSummary(run.resultSummaryJson(), releaseSummary),
                run.errorCode(),
                run.errorSummary(),
                run.createdBy(),
                run.startedAt(),
                run.finishedAt(),
                run.createdAt(),
                now
        );
        repository.updateRun(updated);
        return updated;
    }

    private ReleaseAttempt releaseLease(ExecutionRun run, UUID accountLeaseRef) {
        String accountStatus = releaseAccountStatus(run.status());
        if (testDataService == null) {
            return ReleaseAttempt.failed(
                    accountLeaseRef,
                    "EXECUTION_ACCOUNT_LEASE_ADAPTER_UNAVAILABLE",
                    "WP8 account lease adapter is unavailable",
                    accountStatus
            );
        }
        try {
            TestDataExecutionAccountLeaseResponse released = testDataService.releaseExecutionRunLease(
                    accountLeaseRef,
                    new ReleaseExecutionAccountLeaseCommand(
                            run.id().toString(),
                            "wp9 execution run " + run.status().toLowerCase(Locale.ROOT),
                            accountStatus
                    )
            );
            return ReleaseAttempt.succeeded(released, accountStatus);
        } catch (BusinessException exception) {
            return ReleaseAttempt.failed(
                    accountLeaseRef,
                    exception.getErrorCode().name(),
                    SensitiveTextSanitizer.sanitizedErrorSummary(
                            exception.getMessage(),
                            "WP8 account lease release failed",
                            512
                    ),
                    accountStatus
            );
        } catch (RuntimeException exception) {
            return ReleaseAttempt.failed(
                    accountLeaseRef,
                    "EXECUTION_ACCOUNT_LEASE_RELEASE_FAILED",
                    SensitiveTextSanitizer.sanitizedErrorSummary(
                            exception.getMessage(),
                            "WP8 account lease release failed",
                            512
                    ),
                    accountStatus
            );
        }
    }

    private ExecutionNodeRun nodeWithReleaseSummary(ExecutionNodeRun nodeRun, ReleaseAttempt attempt, Instant now) {
        return new ExecutionNodeRun(
                nodeRun.id(),
                nodeRun.runId(),
                nodeRun.planNodeId(),
                nodeRun.status(),
                nodeRun.attempt(),
                nodeRun.runnerType(),
                nodeRun.externalRunId(),
                nodeRun.errorCode(),
                nodeRun.errorSummary(),
                jsonSupport.mergedSummary(nodeRun.resultSummaryJson(), attempt.summary(now)),
                nodeRun.heartbeatAt(),
                nodeRun.queuedAt(),
                nodeRun.startedAt(),
                nodeRun.finishedAt(),
                nodeRun.createdAt(),
                now
        );
    }

    private Map<String, Object> acquisitionSummary(
            TestDataExecutionAccountLeaseResponse lease,
            AccountLeaseInput leaseInput,
            String requestKey,
            Instant now
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("accountLeaseRequired", true);
        summary.put("accountLeaseAcquired", true);
        summary.put("accountLeaseRef", lease.accountLeaseRef().toString());
        summary.put("accountPoolRef", leaseInput.accountPoolRef().toString());
        summary.put("accountLeaseRequestKey", requestKey);
        summary.put("accountLeaseStatus", lease.status());
        summary.put("accountLeaseExpiresAt", stringInstant(lease.expiresAt()));
        summary.put("accountLeaseReleasedAt", stringInstant(lease.releasedAt()));
        summary.put("accountLeaseAcquiredAt", now.toString());
        summary.put("accountLeasePolicy", safePolicy(lease.policy()));
        summary.put("accountLeaseAccount", safeAccountSummary(lease.account()));
        summary.put("accountLeaseSecretRefDigest", lease.account() == null ? null : lease.account().secretRefDigest());
        summary.put("accountLeaseTokenStored", false);
        summary.put("accountLeaseSecretPlaintextStored", false);
        return summary;
    }

    private Map<String, Object> safeAccountSummary(TestDataCrossWpAccountSummary account) {
        if (account == null) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("accountRef", account.accountRef() == null ? null : account.accountRef().toString());
        summary.put("accountPoolRef", account.accountPoolRef() == null ? null : account.accountPoolRef().toString());
        summary.put("projectId", bounded(account.projectId(), 64));
        summary.put("accountKey", bounded(account.accountKey(), 128));
        summary.put("displayName", bounded(account.displayName(), 128));
        summary.put("status", bounded(account.status(), 32));
        summary.put("roleTags", safeStringList(account.roleTags(), 64, MAX_ROLE_TAGS));
        summary.put("scopeSummaryKeys", account.scopeSummary() == null
                ? List.of()
                : account.scopeSummary().keySet().stream().map(String::valueOf).sorted().toList());
        summary.put("secretRefDigest", bounded(account.secretRefDigest(), 128));
        summary.put("lastHealthStatus", bounded(account.lastHealthStatus(), 64));
        return summary;
    }

    private Map<String, Object> safePolicy(Map<String, Object> policy) {
        if (policy == null || policy.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        policy.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (!StringUtils.hasText(entry.getKey()) || sensitiveKey(entry.getKey())) {
                        return;
                    }
                    Object value = entry.getValue();
                    if (value == null || value instanceof Boolean || value instanceof Number) {
                        safe.put(entry.getKey(), value);
                    } else {
                        safe.put(entry.getKey(), bounded(String.valueOf(value), 128));
                    }
                });
        return safe;
    }

    private AccountLeaseInput accountLeaseInput(Map<String, Object> planInput) {
        Object raw = planInput == null ? null : planInput.get("accountLease");
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        UUID accountPoolRef = uuid(text(map.get("accountPoolRef")));
        if (accountPoolRef == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_ACCOUNT_LEASE_POOL_REF_INVALID");
        }
        return new AccountLeaseInput(
                accountPoolRef,
                bounded(text(map.get("applicationId")), 64),
                bounded(text(map.get("environmentId")), 64),
                safeStringList(rawList(map.get("roleTags")), 64, MAX_ROLE_TAGS),
                ttlSeconds(map.get("ttlSeconds")),
                bounded(text(map.get("requestKey")), 64)
        );
    }

    private boolean existingActiveLease(Map<String, Object> summary) {
        if (accountLeaseRef(summary) == null) {
            return false;
        }
        String status = text(summary.get("accountLeaseStatus"));
        if (!ACTIVE_LEASE_STATUSES.contains(status)) {
            return false;
        }
        Instant expiresAt = instant(text(summary.get("accountLeaseExpiresAt")));
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_ACCOUNT_LEASE_EXPIRED");
        }
        return true;
    }

    private boolean alreadyReleased(Map<String, Object> summary) {
        String status = text(summary.get("accountLeaseStatus"));
        return "RELEASED".equals(status) || StringUtils.hasText(text(summary.get("accountLeaseReleasedAt")));
    }

    private UUID accountLeaseRef(Map<String, Object> summary) {
        return summary == null ? null : uuid(text(summary.get("accountLeaseRef")));
    }

    private String requestKey(
            ExecutionRun run,
            ExecutionPlanNode planNode,
            ExecutionNodeRun nodeRun,
            AccountLeaseInput leaseInput
    ) {
        String seedDigest = StringUtils.hasText(leaseInput.requestKey())
                ? ":rk-" + SensitiveTextSanitizer.sha256Hex(leaseInput.requestKey()).substring(0, 12)
                : "";
        return SensitiveTextSanitizer.boundedText(
                "wp9:" + run.id() + ":" + planNode.id() + ":" + nodeRun.attempt() + seedDigest,
                128
        );
    }

    private int defaultLeaseTtlSeconds(ExecutionPlanNode planNode) {
        int timeoutSeconds = planNode.timeoutSeconds() <= 0
                ? properties.effectiveDefaultRunTimeoutSeconds()
                : planNode.timeoutSeconds();
        return Math.min(timeoutSeconds + properties.effectiveNodeHeartbeatTimeoutSeconds(), MAX_TTL_SECONDS);
    }

    private String releaseAccountStatus(String runStatus) {
        return "SUCCEEDED".equals(runStatus) ? "AVAILABLE" : "LOCKED";
    }

    private Integer ttlSeconds(Object value) {
        if (value == null) {
            return null;
        }
        int parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException exception) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_ACCOUNT_LEASE_TTL_INVALID");
            }
        }
        if (parsed < 1 || parsed > MAX_TTL_SECONDS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_ACCOUNT_LEASE_TTL_INVALID");
        }
        return parsed;
    }

    private List<String> safeStringList(List<?> values, int maxLength, int maxItems) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            String text = bounded(value == null ? null : String.valueOf(value), maxLength);
            if (StringUtils.hasText(text) && !result.contains(text)) {
                result.add(text);
            }
            if (result.size() >= maxItems) {
                break;
            }
        }
        return result;
    }

    private List<?> rawList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        iterable.forEach(result::add);
        return result;
    }

    private UUID uuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Instant instant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String stringInstant(Instant value) {
        return value == null ? null : value.toString();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String bounded(String value, int maxLength) {
        return SensitiveTextSanitizer.boundedNullableText(value, maxLength);
    }

    private boolean sensitiveKey(String key) {
        String normalized = key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        return normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("password")
                || normalized.contains("cookie")
                || normalized.contains("authorization");
    }

    private record AccountLeaseInput(
            UUID accountPoolRef,
            String applicationId,
            String environmentId,
            List<String> roleTags,
            Integer ttlSeconds,
            String requestKey
    ) {
    }

    private record ReleaseAttempt(
            boolean success,
            UUID accountLeaseRef,
            String status,
            Instant expiresAt,
            Instant releasedAt,
            String errorCode,
            String errorSummary,
            String accountStatus
    ) {

        private static ReleaseAttempt succeeded(TestDataExecutionAccountLeaseResponse lease, String accountStatus) {
            return new ReleaseAttempt(
                    true,
                    lease.accountLeaseRef(),
                    lease.status(),
                    lease.expiresAt(),
                    lease.releasedAt(),
                    null,
                    null,
                    accountStatus
            );
        }

        private static ReleaseAttempt failed(
                UUID accountLeaseRef,
                String errorCode,
                String errorSummary,
                String accountStatus
        ) {
            return new ReleaseAttempt(
                    false,
                    accountLeaseRef,
                    null,
                    null,
                    null,
                    errorCode,
                    errorSummary,
                    accountStatus
            );
        }

        private Map<String, Object> summary(Instant now) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("accountLeaseReleaseAttempted", true);
            summary.put("accountLeaseReleaseSucceeded", success);
            summary.put("accountLeaseReleaseAccountStatus", accountStatus);
            summary.put("accountLeaseReleaseAttemptedAt", now.toString());
            if (success) {
                summary.put("accountLeaseStatus", status);
                summary.put("accountLeaseExpiresAt", expiresAt == null ? null : expiresAt.toString());
                summary.put("accountLeaseReleasedAt", releasedAt == null ? now.toString() : releasedAt.toString());
                summary.put("accountLeaseReleaseFailed", false);
            } else {
                summary.put("accountLeaseReleaseFailed", true);
                summary.put("accountLeaseReleaseErrorCode", errorCode);
                summary.put("accountLeaseReleaseErrorSummary", errorSummary);
                summary.put("accountLeaseRef", accountLeaseRef == null ? null : accountLeaseRef.toString());
            }
            return summary;
        }
    }
}
