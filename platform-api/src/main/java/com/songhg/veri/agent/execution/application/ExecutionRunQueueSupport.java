package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.execution.application.command.CompleteExecutionNodeRunCommand;
import com.songhg.veri.agent.execution.application.command.HeartbeatExecutionQueueClaimCommand;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.view.ExecutionQueueClaimResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionQueueRecoveryResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionQueueClaim;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * Owns WP9 execution queue claims, heartbeat recovery, and run aggregation state transitions.
 */
final class ExecutionRunQueueSupport {

    private static final Set<String> TERMINAL_RUN_STATUSES = Set.of(
            "SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "CANCELED", "TIMEOUT"
    );
    private static final Set<String> COMPLETABLE_NODE_STATUSES = Set.of(
            "SUCCEEDED", "SKIPPED", "FAILED", "TIMEOUT", "BLOCKED"
    );
    private static final Set<String> SUCCESS_NODE_STATUSES = Set.of("SUCCEEDED", "SKIPPED");
    private static final Set<String> FAILURE_NODE_STATUSES = Set.of("FAILED", "TIMEOUT", "BLOCKED");
    private static final Set<String> ACTIVE_NODE_STATUSES = Set.of("PENDING", "QUEUED", "RUNNING");
    private static final Set<String> FORBIDDEN_RESULT_SUMMARY_KEYS = Set.of(
            "secret", "secrets", "token", "password", "authorization", "stdout", "stderr",
            "request", "requestbody", "response", "responsebody", "body", "variables", "environment"
    );
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("[A-Z0-9_.:-]{1,64}");
    private static final int MAX_RESULT_SUMMARY_TEXT_LENGTH = 512;
    private static final int MAX_RESULT_SUMMARY_LIST_ITEMS = 20;

    private final ExecutionRepository repository;
    private final ExecutionPlatformContextClient contextClient;
    private final ExecutionProperties properties;
    private final ExecutionRunJsonSupport jsonSupport;
    private final ExecutionRunResponseMapper responseMapper;

    ExecutionRunQueueSupport(
            ExecutionRepository repository,
            ExecutionPlatformContextClient contextClient,
            ExecutionProperties properties,
            ExecutionRunJsonSupport jsonSupport,
            ExecutionRunResponseMapper responseMapper
    ) {
        this.repository = repository;
        this.contextClient = contextClient;
        this.properties = properties;
        this.jsonSupport = jsonSupport;
        this.responseMapper = responseMapper;
    }

    Optional<ExecutionQueueClaimResponse> claimNextQueuedNode(String workerId) {
        String normalizedWorkerId = SensitiveTextSanitizer.boundedNullableText(workerId, 128);
        if (!StringUtils.hasText(normalizedWorkerId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_QUEUE_WORKER_REQUIRED");
        }
        Instant now = Instant.now();
        for (ExecutionNodeRun candidate : repository.queuedNodeRuns(properties.effectiveRecoveryBatchSize())) {
            Optional<ExecutionQueueClaimResponse> claimed = claimQueuedNode(candidate, normalizedWorkerId, now);
            if (claimed.isPresent()) {
                return claimed;
            }
        }
        return Optional.empty();
    }

    ExecutionRunDetailResponse completeClaimedNodeRun(CompleteExecutionNodeRunCommand command) {
        if (command == null || command.nodeRunId() == null || !StringUtils.hasText(command.claimToken())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_QUEUE_CLAIM_REQUIRED");
        }
        String targetStatus = Optional.ofNullable(SensitiveTextSanitizer.boundedNullableText(command.status(), 32))
                .map(status -> status.toUpperCase(java.util.Locale.ROOT))
                .orElse(null);
        if (!COMPLETABLE_NODE_STATUSES.contains(targetStatus)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_NODE_STATUS_INVALID");
        }
        ExecutionQueueClaim claim = repository.queueClaimByToken(command.claimToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_INVALID"));
        if (!command.nodeRunId().equals(claim.nodeRunId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_NODE_MISMATCH");
        }
        ExecutionNodeRun nodeRun = repository.nodeRun(command.nodeRunId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行节点运行不存在"));
        Instant now = Instant.now();
        if ("COMPLETED".equals(claim.status()) && COMPLETABLE_NODE_STATUSES.contains(nodeRun.status())) {
            return detail(requireRun(nodeRun.runId()), false);
        }
        if (!"CLAIMED".equals(claim.status()) || !claim.expiresAt().isAfter(now) || !"RUNNING".equals(nodeRun.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_NOT_ACTIVE");
        }
        ExecutionNodeRun completed = new ExecutionNodeRun(
                nodeRun.id(),
                nodeRun.runId(),
                nodeRun.planNodeId(),
                targetStatus,
                nodeRun.attempt(),
                nodeRun.runnerType(),
                nodeRun.externalRunId(),
                terminalErrorCode(targetStatus, command.errorCode()),
                terminalErrorSummary(targetStatus, command.errorSummary()),
                jsonSupport.mergedSummary(nodeRun.resultSummaryJson(), sanitizedCompletionSummary(command, now)),
                now,
                nodeRun.queuedAt(),
                nodeRun.startedAt(),
                now,
                nodeRun.createdAt(),
                now
        );
        repository.updateNodeRuns(List.of(completed));
        repository.updateQueueClaim(new ExecutionQueueClaim(
                claim.id(),
                claim.nodeRunId(),
                claim.claimToken(),
                claim.workerId(),
                claim.claimedAt(),
                now,
                claim.expiresAt(),
                "COMPLETED",
                claim.createdAt(),
                now
        ));
        return aggregateRunAfterNodeCompletion(completed.runId(), now);
    }

    ExecutionQueueClaimResponse heartbeatQueueClaim(HeartbeatExecutionQueueClaimCommand command) {
        if (command == null || !StringUtils.hasText(command.claimToken())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_QUEUE_CLAIM_REQUIRED");
        }
        ExecutionQueueClaim claim = repository.queueClaimByToken(command.claimToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_INVALID"));
        ExecutionNodeRun nodeRun = repository.nodeRun(claim.nodeRunId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行节点运行不存在"));
        Instant now = Instant.now();
        if (!"CLAIMED".equals(claim.status()) || !claim.expiresAt().isAfter(now) || !"RUNNING".equals(nodeRun.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_NOT_ACTIVE");
        }
        ExecutionQueueClaim renewedClaim = new ExecutionQueueClaim(
                claim.id(),
                claim.nodeRunId(),
                claim.claimToken(),
                claim.workerId(),
                claim.claimedAt(),
                now,
                now.plusSeconds(properties.effectiveNodeHeartbeatTimeoutSeconds()),
                "CLAIMED",
                claim.createdAt(),
                now
        );
        if (!repository.updateQueueClaimIfStatus(renewedClaim, "CLAIMED")) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_NOT_ACTIVE");
        }
        ExecutionNodeRun heartbeatNode = new ExecutionNodeRun(
                nodeRun.id(),
                nodeRun.runId(),
                nodeRun.planNodeId(),
                nodeRun.status(),
                nodeRun.attempt(),
                nodeRun.runnerType(),
                nodeRun.externalRunId(),
                nodeRun.errorCode(),
                nodeRun.errorSummary(),
                jsonSupport.mergedSummary(nodeRun.resultSummaryJson(), Map.of(
                        "lastHeartbeatAt", now.toString(),
                        "runnerDispatched", false
                )),
                now,
                nodeRun.queuedAt(),
                nodeRun.startedAt(),
                nodeRun.finishedAt(),
                nodeRun.createdAt(),
                now
        );
        if (!repository.updateNodeRunIfStatus(heartbeatNode, "RUNNING")) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_NOT_ACTIVE");
        }
        ExecutionRun run = requireRun(nodeRun.runId());
        ExecutionPlanNode planNode = repository.planNodes(run.planId()).stream()
                .filter(node -> node.id().equals(nodeRun.planNodeId()))
                .findFirst()
                .orElse(null);
        return toQueueClaimResponse(renewedClaim, heartbeatNode, run, planNode);
    }

    ExecutionQueueRecoveryResponse recoverExpiredQueueClaims() {
        Instant now = Instant.now();
        RecoveryCounters counters = new RecoveryCounters(now);
        Set<UUID> processedNodeRunIds = new HashSet<>();
        for (ExecutionQueueClaim claim : repository.expiredQueueClaims(now, properties.effectiveRecoveryBatchSize())) {
            processedNodeRunIds.add(claim.nodeRunId());
            recoverExpiredClaim(claim, now, counters);
        }
        for (ExecutionNodeRun staleNode : repository.runningNodeRunsStartedBefore(
                now,
                properties.effectiveRecoveryBatchSize()
        )) {
            if (processedNodeRunIds.contains(staleNode.id())) {
                continue;
            }
            if (repository.activeQueueClaim(staleNode.id())
                    .filter(claim -> claim.expiresAt().isAfter(now))
                    .isPresent()) {
                continue;
            }
            recoverUnclaimedStaleNode(staleNode, now, counters);
        }
        return counters.toResponse();
    }

    ExecutionRunDetailResponse aggregateRunAfterNodeCompletion(UUID runId, Instant now) {
        ExecutionRun run = requireRun(runId);
        List<ExecutionPlanNode> planNodes = repository.planNodes(run.planId());
        List<ExecutionNodeRun> nodeRuns = repository.nodeRuns(run.id());
        Map<UUID, ExecutionPlanNode> planNodeById = planNodes.stream()
                .collect(Collectors.toMap(ExecutionPlanNode::id, Function.identity()));
        Map<String, ExecutionNodeRun> latestByKey = latestNodeRuns(nodeRuns).stream()
                .filter(nodeRun -> planNodeById.containsKey(nodeRun.planNodeId()))
                .collect(Collectors.toMap(
                        nodeRun -> planNodeById.get(nodeRun.planNodeId()).nodeKey(),
                        Function.identity(),
                        (left, right) -> left.attempt() >= right.attempt() ? left : right
                ));
        List<ExecutionNodeRun> dependencyUpdates = planNodes.stream()
                .map(node -> downstreamUpdate(node, latestByKey, planNodeById, now))
                .flatMap(Optional::stream)
                .toList();
        if (!dependencyUpdates.isEmpty()) {
            repository.updateNodeRuns(dependencyUpdates);
            nodeRuns = repository.nodeRuns(run.id());
        }
        ExecutionRun aggregated = aggregateRun(run, latestNodeRuns(nodeRuns), now);
        repository.updateRun(aggregated);
        if (TERMINAL_RUN_STATUSES.contains(aggregated.status())
                && !TERMINAL_RUN_STATUSES.contains(run.status())) {
            auditRun(aggregated, "execution.run.completed", "SUCCESS", Map.of(
                    "status", aggregated.status(),
                    "summary", jsonSupport.readMap(aggregated.resultSummaryJson())
            ));
        }
        return detail(requireRun(runId), false);
    }

    List<ExecutionNodeRun> latestNodeRuns(List<ExecutionNodeRun> nodeRuns) {
        return nodeRuns.stream()
                .collect(Collectors.toMap(
                        ExecutionNodeRun::planNodeId,
                        Function.identity(),
                        (left, right) -> left.attempt() >= right.attempt() ? left : right
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(ExecutionNodeRun::createdAt))
                .toList();
    }

    String terminalErrorCode(String status, String requestedErrorCode) {
        if (SUCCESS_NODE_STATUSES.contains(status)) {
            return null;
        }
        if (StringUtils.hasText(requestedErrorCode)) {
            String normalized = SensitiveTextSanitizer.boundedNullableText(requestedErrorCode, 64);
            if (!SensitiveTextSanitizer.containsSensitiveText(normalized)
                    && ERROR_CODE_PATTERN.matcher(normalized).matches()) {
                return normalized;
            }
        }
        return switch (status) {
            case "TIMEOUT" -> "EXECUTION_RUN_TIMEOUT";
            case "BLOCKED" -> "EXECUTION_DEPENDENCY_BLOCKED";
            default -> "EXECUTION_NODE_FAILED";
        };
    }

    String terminalErrorSummary(String status, String requestedErrorSummary) {
        String fallback = switch (status) {
            case "TIMEOUT" -> "Execution node timed out";
            case "BLOCKED" -> "Execution node blocked";
            default -> "Execution node failed";
        };
        if (SUCCESS_NODE_STATUSES.contains(status)) {
            return null;
        }
        // Error summaries are persisted in node evidence, so URL, secret-ref and token redaction must stay centralized.
        return SensitiveTextSanitizer.sanitizedErrorSummary(requestedErrorSummary, fallback, 512);
    }

    private Optional<ExecutionQueueClaimResponse> claimQueuedNode(
            ExecutionNodeRun candidate,
            String workerId,
            Instant now
    ) {
        ExecutionRun run = repository.run(candidate.runId()).orElse(null);
        if (run == null || !Set.of("QUEUED", "RUNNING").contains(run.status())) {
            return Optional.empty();
        }
        ExecutionQueueClaim claim = new ExecutionQueueClaim(
                UUID.randomUUID(),
                candidate.id(),
                "wp9_claim_" + UUID.randomUUID().toString().replace("-", ""),
                workerId,
                now,
                now,
                now.plusSeconds(properties.effectiveNodeHeartbeatTimeoutSeconds()),
                "CLAIMED",
                now,
                now
        );
        if (!repository.tryInsertQueueClaim(claim)) {
            return Optional.empty();
        }
        ExecutionNodeRun runningNode = new ExecutionNodeRun(
                candidate.id(),
                candidate.runId(),
                candidate.planNodeId(),
                "RUNNING",
                candidate.attempt(),
                candidate.runnerType(),
                candidate.externalRunId(),
                null,
                null,
                jsonSupport.mergedSummary(candidate.resultSummaryJson(), Map.of(
                        "schedulerClaimCreated", true,
                        "runnerDispatched", false
                )),
                now,
                candidate.queuedAt(),
                now,
                null,
                candidate.createdAt(),
                now
        );
        if (!repository.updateNodeRunIfStatus(runningNode, "QUEUED")) {
            releaseClaim(claim, now);
            return Optional.empty();
        }
        ExecutionRun runningRun = run;
        if ("QUEUED".equals(run.status())) {
            runningRun = new ExecutionRun(
                    run.id(),
                    run.planId(),
                    run.projectId(),
                    "RUNNING",
                    run.triggerType(),
                    run.requestKey(),
                    run.sourceEventId(),
                    run.attempt(),
                    run.traceId(),
                    jsonSupport.mergedSummary(run.resultSummaryJson(), Map.of(
                            "schedulerClaimCreated", true,
                            "runnerDispatched", false,
                            "stateAggregationReady", true
                    )),
                    null,
                    null,
                    run.createdBy(),
                    now,
                    null,
                    run.createdAt(),
                    now
            );
            repository.updateRun(runningRun);
            auditRun(runningRun, "execution.run.started", "SUCCESS", Map.of(
                    "status", runningRun.status(),
                    "nodeRunId", candidate.id().toString(),
                    "runnerDispatched", false
            ));
        }
        ExecutionPlanNode planNode = repository.planNodes(run.planId()).stream()
                .filter(node -> node.id().equals(candidate.planNodeId()))
                .findFirst()
                .orElse(null);
        return Optional.of(toQueueClaimResponse(claim, runningNode, runningRun, planNode));
    }

    private void releaseClaim(ExecutionQueueClaim claim, Instant now) {
        repository.updateQueueClaimIfStatus(new ExecutionQueueClaim(
                claim.id(),
                claim.nodeRunId(),
                claim.claimToken(),
                claim.workerId(),
                claim.claimedAt(),
                now,
                claim.expiresAt(),
                "RELEASED",
                claim.createdAt(),
                now
        ), "CLAIMED");
    }

    private ExecutionQueueClaimResponse toQueueClaimResponse(
            ExecutionQueueClaim claim,
            ExecutionNodeRun nodeRun,
            ExecutionRun run,
            ExecutionPlanNode planNode
    ) {
        return new ExecutionQueueClaimResponse(
                claim.id(),
                run.id(),
                nodeRun.id(),
                nodeRun.planNodeId(),
                planNode == null ? null : planNode.nodeKey(),
                nodeRun.runnerType(),
                claim.claimToken(),
                claim.workerId(),
                claim.claimedAt(),
                claim.heartbeatAt(),
                claim.expiresAt()
        );
    }

    private void recoverExpiredClaim(ExecutionQueueClaim claim, Instant now, RecoveryCounters counters) {
        if (!repository.updateExpiredQueueClaim(expiredClaim(claim, now), now)) {
            return;
        }
        counters.expiredClaimCount++;
        repository.nodeRun(claim.nodeRunId())
                .filter(nodeRun -> "RUNNING".equals(nodeRun.status()))
                .ifPresent(nodeRun -> recoverRunningNode(nodeRun, now, counters, true));
    }

    private ExecutionQueueClaim expiredClaim(ExecutionQueueClaim claim, Instant now) {
        return new ExecutionQueueClaim(
                claim.id(),
                claim.nodeRunId(),
                claim.claimToken(),
                claim.workerId(),
                claim.claimedAt(),
                claim.heartbeatAt(),
                claim.expiresAt(),
                "EXPIRED",
                claim.createdAt(),
                now
        );
    }

    private void recoverUnclaimedStaleNode(ExecutionNodeRun nodeRun, Instant now, RecoveryCounters counters) {
        if (!nodeTimedOut(nodeRun, planNode(nodeRun), now)) {
            return;
        }
        recoverRunningNode(nodeRun, now, counters, false);
    }

    private void recoverRunningNode(
            ExecutionNodeRun nodeRun,
            Instant now,
            RecoveryCounters counters,
            boolean allowRequeue
    ) {
        ExecutionPlanNode planNode = planNode(nodeRun);
        ExecutionNodeRun recovered = allowRequeue && !nodeTimedOut(nodeRun, planNode, now)
                ? requeuedExpiredNodeRun(nodeRun, now)
                : timedOutNodeRun(nodeRun, now);
        if (!repository.updateNodeRunIfStatus(recovered, "RUNNING")) {
            return;
        }
        if ("QUEUED".equals(recovered.status())) {
            counters.requeuedNodeCount++;
        } else {
            counters.timedOutNodeCount++;
        }
        counters.aggregatedRunIds.add(recovered.runId());
        aggregateRunAfterNodeCompletion(recovered.runId(), now);
    }

    private ExecutionPlanNode planNode(ExecutionNodeRun nodeRun) {
        return repository.run(nodeRun.runId())
                .map(run -> repository.planNodes(run.planId()).stream()
                        .filter(node -> node.id().equals(nodeRun.planNodeId()))
                        .findFirst()
                        .orElse(null))
                .orElse(null);
    }

    private boolean nodeTimedOut(ExecutionNodeRun nodeRun, ExecutionPlanNode planNode, Instant now) {
        int timeoutSeconds = planNode == null || planNode.timeoutSeconds() <= 0
                ? properties.effectiveDefaultRunTimeoutSeconds()
                : planNode.timeoutSeconds();
        Instant timeoutBase = nodeRun.startedAt() == null ? nodeRun.createdAt() : nodeRun.startedAt();
        return timeoutBase != null && !timeoutBase.plusSeconds(timeoutSeconds).isAfter(now);
    }

    private ExecutionNodeRun requeuedExpiredNodeRun(ExecutionNodeRun nodeRun, Instant now) {
        return new ExecutionNodeRun(
                nodeRun.id(),
                nodeRun.runId(),
                nodeRun.planNodeId(),
                "QUEUED",
                nodeRun.attempt(),
                nodeRun.runnerType(),
                nodeRun.externalRunId(),
                null,
                null,
                jsonSupport.mergedSummary(nodeRun.resultSummaryJson(), Map.of(
                        "claimExpired", true,
                        "recoveryAction", "REQUEUED",
                        "runnerDispatched", false
                )),
                null,
                now,
                null,
                null,
                nodeRun.createdAt(),
                now
        );
    }

    private ExecutionNodeRun timedOutNodeRun(ExecutionNodeRun nodeRun, Instant now) {
        return new ExecutionNodeRun(
                nodeRun.id(),
                nodeRun.runId(),
                nodeRun.planNodeId(),
                "TIMEOUT",
                nodeRun.attempt(),
                nodeRun.runnerType(),
                nodeRun.externalRunId(),
                "EXECUTION_RUN_TIMEOUT",
                "Execution node timed out during queue recovery",
                jsonSupport.mergedSummary(nodeRun.resultSummaryJson(), Map.of(
                        "claimExpired", true,
                        "recoveryAction", "TIMED_OUT",
                        "runnerDispatched", false
                )),
                nodeRun.heartbeatAt(),
                nodeRun.queuedAt(),
                nodeRun.startedAt(),
                now,
                nodeRun.createdAt(),
                now
        );
    }

    private Optional<ExecutionNodeRun> downstreamUpdate(
            ExecutionPlanNode node,
            Map<String, ExecutionNodeRun> latestByKey,
            Map<UUID, ExecutionPlanNode> planNodeById,
            Instant now
    ) {
        ExecutionNodeRun nodeRun = latestByKey.get(node.nodeKey());
        if (nodeRun == null || !"PENDING".equals(nodeRun.status())) {
            return Optional.empty();
        }
        DependencyState state = dependencyState(node, latestByKey, planNodeById);
        if (state.blockedByDependencyKey() != null) {
            return Optional.of(updatedPendingNode(
                    nodeRun,
                    "BLOCKED",
                    jsonSupport.mergedSummary(nodeRun.resultSummaryJson(), Map.of(
                            "blockedByDependencyKey", state.blockedByDependencyKey(),
                            "dispatchReady", false
                    )),
                    null,
                    now
            ));
        }
        if (state.ready()) {
            return Optional.of(updatedPendingNode(
                    nodeRun,
                    "QUEUED",
                    jsonSupport.mergedSummary(nodeRun.resultSummaryJson(), Map.of(
                            "dependenciesSatisfied", true,
                            "dispatchReady", false
                    )),
                    now,
                    now
            ));
        }
        return Optional.empty();
    }

    private DependencyState dependencyState(
            ExecutionPlanNode node,
            Map<String, ExecutionNodeRun> latestByKey,
            Map<UUID, ExecutionPlanNode> planNodeById
    ) {
        Set<String> waitingFor = new HashSet<>(node.dependencyKeys());
        for (String dependencyKey : node.dependencyKeys()) {
            ExecutionNodeRun dependencyRun = latestByKey.get(dependencyKey);
            if (dependencyRun == null || ACTIVE_NODE_STATUSES.contains(dependencyRun.status())) {
                continue;
            }
            ExecutionPlanNode dependencyNode = planNodeById.get(dependencyRun.planNodeId());
            if (FAILURE_NODE_STATUSES.contains(dependencyRun.status())
                    && (dependencyNode == null || !"CONTINUE".equals(dependencyNode.failurePolicy()))) {
                return new DependencyState(false, dependencyKey);
            }
            if (SUCCESS_NODE_STATUSES.contains(dependencyRun.status())
                    || (FAILURE_NODE_STATUSES.contains(dependencyRun.status())
                    && dependencyNode != null
                    && "CONTINUE".equals(dependencyNode.failurePolicy()))) {
                waitingFor.remove(dependencyKey);
            }
        }
        return new DependencyState(waitingFor.isEmpty(), null);
    }

    private ExecutionNodeRun updatedPendingNode(
            ExecutionNodeRun nodeRun,
            String status,
            String summary,
            Instant queuedAt,
            Instant now
    ) {
        return new ExecutionNodeRun(
                nodeRun.id(),
                nodeRun.runId(),
                nodeRun.planNodeId(),
                status,
                nodeRun.attempt(),
                nodeRun.runnerType(),
                nodeRun.externalRunId(),
                "BLOCKED".equals(status) ? "EXECUTION_DEPENDENCY_BLOCKED" : null,
                "BLOCKED".equals(status) ? "Execution node blocked by failed dependency" : null,
                summary,
                nodeRun.heartbeatAt(),
                queuedAt,
                nodeRun.startedAt(),
                "BLOCKED".equals(status) ? now : null,
                nodeRun.createdAt(),
                now
        );
    }

    private ExecutionRun aggregateRun(ExecutionRun run, List<ExecutionNodeRun> latestNodeRuns, Instant now) {
        long succeeded = latestNodeRuns.stream().filter(nodeRun -> "SUCCEEDED".equals(nodeRun.status())).count();
        long skipped = latestNodeRuns.stream().filter(nodeRun -> "SKIPPED".equals(nodeRun.status())).count();
        long failed = latestNodeRuns.stream().filter(nodeRun -> "FAILED".equals(nodeRun.status())).count();
        long timedOut = latestNodeRuns.stream().filter(nodeRun -> "TIMEOUT".equals(nodeRun.status())).count();
        long blocked = latestNodeRuns.stream().filter(nodeRun -> "BLOCKED".equals(nodeRun.status())).count();
        long running = latestNodeRuns.stream().filter(nodeRun -> "RUNNING".equals(nodeRun.status())).count();
        long queued = latestNodeRuns.stream().filter(nodeRun -> "QUEUED".equals(nodeRun.status())).count();
        long pending = latestNodeRuns.stream().filter(nodeRun -> "PENDING".equals(nodeRun.status())).count();
        long successful = succeeded + skipped;
        long failedLike = failed + blocked;
        boolean active = running + queued + pending > 0;
        String status = aggregateRunStatus(latestNodeRuns.size(), successful, failedLike, timedOut, active);
        return new ExecutionRun(
                run.id(),
                run.planId(),
                run.projectId(),
                status,
                run.triggerType(),
                run.requestKey(),
                run.sourceEventId(),
                run.attempt(),
                run.traceId(),
                jsonSupport.mergedSummary(run.resultSummaryJson(), Map.ofEntries(
                        Map.entry("nodeCount", latestNodeRuns.size()),
                        Map.entry("succeededNodeCount", succeeded),
                        Map.entry("skippedNodeCount", skipped),
                        Map.entry("failedNodeCount", failed),
                        Map.entry("timeoutNodeCount", timedOut),
                        Map.entry("blockedNodeCount", blocked),
                        Map.entry("runningNodeCount", running),
                        Map.entry("queuedNodeCount", queued),
                        Map.entry("pendingNodeCount", pending),
                        Map.entry("stateAggregationReady", true),
                        Map.entry("runnerDispatched", runnerDispatched(latestNodeRuns)),
                        Map.entry("retryInFlight", active && "RETRY".equals(run.triggerType()))
                )),
                terminalRunErrorCode(status),
                terminalRunErrorSummary(status),
                run.createdBy(),
                run.startedAt() == null && !"QUEUED".equals(status) ? now : run.startedAt(),
                TERMINAL_RUN_STATUSES.contains(status) ? now : null,
                run.createdAt(),
                now
        );
    }

    private String aggregateRunStatus(
            int nodeCount,
            long successful,
            long failedLike,
            long timedOut,
            boolean active
    ) {
        if (active) {
            return "RUNNING";
        }
        if (nodeCount > 0 && successful == nodeCount) {
            return "SUCCEEDED";
        }
        if (timedOut > 0) {
            return "TIMEOUT";
        }
        if (failedLike > 0 && successful > 0) {
            return "PARTIAL_SUCCESS";
        }
        if (failedLike > 0) {
            return "FAILED";
        }
        return "FAILED";
    }

    private boolean runnerDispatched(List<ExecutionNodeRun> nodeRuns) {
        return nodeRuns.stream().anyMatch(nodeRun -> StringUtils.hasText(nodeRun.externalRunId())
                || Boolean.TRUE.equals(jsonSupport.readMap(nodeRun.resultSummaryJson()).get("runnerDispatched")));
    }

    private Map<String, Object> sanitizedCompletionSummary(CompleteExecutionNodeRunCommand command, Instant now) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        Map<String, Object> input = command.resultSummary() == null ? Map.of() : command.resultSummary();
        input.forEach((key, value) -> {
            if (StringUtils.hasText(key) && !forbiddenSummaryKey(key)) {
                sanitized.put(key, sanitizedSummaryValue(value));
            }
        });
        sanitized.put("completedStatus", command.status());
        sanitized.put("completedAt", now.toString());
        sanitized.put("rawOutputStored", false);
        sanitized.put("runnerDispatched", false);
        return sanitized;
    }

    private boolean forbiddenSummaryKey(String key) {
        String normalized = key.replace("_", "").replace("-", "").toLowerCase();
        return FORBIDDEN_RESULT_SUMMARY_KEYS.stream().anyMatch(normalized::contains);
    }

    private Object sanitizedSummaryValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String text) {
            return boundedSummaryText(SensitiveTextSanitizer.redactSensitiveText(text));
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                String normalizedKey = key == null ? null : String.valueOf(key);
                if (StringUtils.hasText(normalizedKey) && !forbiddenSummaryKey(normalizedKey)) {
                    sanitized.put(normalizedKey, sanitizedSummaryValue(nestedValue));
                }
            });
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            int count = 0;
            for (Object item : iterable) {
                if (count >= MAX_RESULT_SUMMARY_LIST_ITEMS) {
                    sanitized.add("...");
                    break;
                }
                sanitized.add(sanitizedSummaryValue(item));
                count++;
            }
            return sanitized;
        }
        return boundedSummaryText(SensitiveTextSanitizer.redactSensitiveText(String.valueOf(value)));
    }

    private String boundedSummaryText(String value) {
        return SensitiveTextSanitizer.boundedWithEllipsis(value, MAX_RESULT_SUMMARY_TEXT_LENGTH);
    }

    private String terminalRunErrorCode(String status) {
        return switch (status) {
            case "FAILED", "PARTIAL_SUCCESS" -> "EXECUTION_RUN_FAILED";
            case "TIMEOUT" -> "EXECUTION_RUN_TIMEOUT";
            default -> null;
        };
    }

    private String terminalRunErrorSummary(String status) {
        return switch (status) {
            case "FAILED" -> "Execution run failed";
            case "PARTIAL_SUCCESS" -> "Execution run partially succeeded";
            case "TIMEOUT" -> "Execution run timed out";
            default -> null;
        };
    }

    private ExecutionRunDetailResponse detail(ExecutionRun run, boolean idempotentReplay) {
        return responseMapper.toDetail(
                run,
                idempotentReplay,
                repository.nodeRuns(run.id()),
                repository.planNodes(run.planId())
        );
    }

    private ExecutionRun requireRun(UUID id) {
        return repository.run(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行运行不存在"));
    }

    private void auditRun(ExecutionRun run, String action, String result, Map<String, Object> afterJson) {
        contextClient.writeAuditEvent(action, "EXECUTION_RUN", run.id().toString(), run.projectId(), result, afterJson);
    }

    private record DependencyState(boolean ready, String blockedByDependencyKey) {
    }

    private static final class RecoveryCounters {
        private final Instant recoveredAt;
        private final Set<UUID> aggregatedRunIds = new HashSet<>();
        private int expiredClaimCount;
        private int requeuedNodeCount;
        private int timedOutNodeCount;

        private RecoveryCounters(Instant recoveredAt) {
            this.recoveredAt = recoveredAt;
        }

        private ExecutionQueueRecoveryResponse toResponse() {
            return new ExecutionQueueRecoveryResponse(
                    expiredClaimCount,
                    requeuedNodeCount,
                    timedOutNodeCount,
                    aggregatedRunIds.size(),
                    recoveredAt
            );
        }
    }
}
