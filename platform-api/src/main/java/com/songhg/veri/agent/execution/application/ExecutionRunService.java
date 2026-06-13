package com.songhg.veri.agent.execution.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.ApiAutomationService;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationRunCommand;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunResultResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.execution.application.command.CompleteExecutionNodeRunCommand;
import com.songhg.veri.agent.execution.application.command.DispatchExecutionNodeRunCommand;
import com.songhg.veri.agent.execution.application.command.HeartbeatExecutionQueueClaimCommand;
import com.songhg.veri.agent.execution.application.command.TriggerExecutionRunCommand;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.query.ExecutionRunPageRequest;
import com.songhg.veri.agent.execution.application.query.ExecutionRunQuery;
import com.songhg.veri.agent.execution.application.view.ExecutionNodeRunResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionQueueClaimResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionQueueRecoveryResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunSummaryResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionQueueClaim;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.management.application.port.ManagementStoreParams;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentRuntimeRef;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class ExecutionRunService {

    private static final Set<String> RUN_STATUSES = Set.of(
            "QUEUED", "RUNNING", "SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "CANCELED", "TIMEOUT"
    );
    private static final Set<String> TERMINAL_RUN_STATUSES = Set.of(
            "SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "CANCELED", "TIMEOUT"
    );
    private static final Set<String> RETRYABLE_RUN_STATUSES = Set.of("FAILED", "PARTIAL_SUCCESS", "TIMEOUT");
    private static final Set<String> CANCELABLE_NODE_STATUSES = Set.of("PENDING", "QUEUED", "RUNNING");
    private static final Set<String> RETRYABLE_NODE_STATUSES = Set.of("FAILED", "TIMEOUT", "BLOCKED");
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
    private static final List<Pattern> SENSITIVE_TEXT_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(api[_-]?key|secret|token|password|passwd|authorization)\\s*[:=]\\s*[^\\s,;，；]+"),
            Pattern.compile("(?i)\\bbearer\\s+[a-z0-9._\\-]{8,}"),
            Pattern.compile("(?i)\\b(sk|pk|rk)_[a-z0-9_-]{8,}\\b")
    );
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("[A-Z0-9_.:-]{1,64}");
    private static final int MAX_RESULT_SUMMARY_TEXT_LENGTH = 512;
    private static final int MAX_RESULT_SUMMARY_LIST_ITEMS = 20;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ExecutionRepository repository;
    private final ExecutionDagValidator dagValidator;
    private final ExecutionPlatformContextClient contextClient;
    private final ExecutionActorResolver actorResolver;
    private final ApiAutomationService apiAutomationService;
    private final ManagementStore managementStore;
    private final ObjectMapper objectMapper;
    private final ExecutionProperties properties;
    private final TransactionTemplate transactionTemplate;

    public ExecutionRunService(
            ExecutionRepository repository,
            ExecutionDagValidator dagValidator,
            ExecutionPlatformContextClient contextClient,
            ExecutionActorResolver actorResolver,
            ApiAutomationService apiAutomationService,
            ObjectProvider<ManagementStore> managementStores,
            ObjectMapper objectMapper,
            ExecutionProperties properties,
            ObjectProvider<PlatformTransactionManager> transactionManagers
    ) {
        this.repository = repository;
        this.dagValidator = dagValidator;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.apiAutomationService = apiAutomationService;
        this.managementStore = managementStores == null ? null : managementStores.getIfAvailable();
        this.objectMapper = objectMapper;
        this.properties = properties;
        PlatformTransactionManager transactionManager = transactionManagers == null
                ? null
                : transactionManagers.getIfAvailable();
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    /**
     * Creates the M3A manual run envelope without claiming work or dispatching any runner.
     *
     * <p>Only READY plans can be triggered, and a repeated requestKey replays the existing run instead of creating
     * duplicate node runs. The stored summary is intentionally limited to counts, DAG digest and trigger metadata; user
     * variables are accepted for future dispatch but not persisted in this slice.</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ExecutionRunDetailResponse triggerManualRun(UUID planId, TriggerExecutionRunCommand command) {
        ExecutionPlan plan = requirePlan(planId);
        if (!"READY".equals(plan.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_PLAN_NOT_READY");
        }
        String requestKey = boundedNullableText(command == null ? null : command.requestKey(), 128);
        if (StringUtils.hasText(requestKey)) {
            return repository.runByPlanAndRequestKey(plan.id(), requestKey)
                    .map(run -> detail(run, true))
                    .orElseGet(() -> createRun(plan, command, requestKey, "MANUAL", null, Map.of()));
        }
        return createRun(plan, command, null, "MANUAL", null, Map.of());
    }

    /**
     * Creates a run for trusted WP9 trigger-control-plane callers without duplicating DAG initialization.
     *
     * <p>The caller owns source validation and passes only sanitized trigger metadata. A bounded internal requestKey is
     * still required for external trigger idempotency so the existing run unique index remains the final duplicate
     * guard if two webhook requests race.</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ExecutionRunDetailResponse triggerExternalRun(
            UUID planId,
            String triggerType,
            String requestKey,
            String sourceEventId,
            Map<String, Object> triggerSummary
    ) {
        ExecutionPlan plan = requirePlan(planId);
        if (!"READY".equals(plan.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_PLAN_NOT_READY");
        }
        String normalizedTriggerType = boundedNullableText(triggerType, 32);
        if (!Set.of("WEBHOOK", "CRON").contains(normalizedTriggerType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_TRIGGER_TYPE_INVALID");
        }
        String normalizedRequestKey = boundedNullableText(requestKey, 128);
        if (!StringUtils.hasText(normalizedRequestKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_TRIGGER_REQUEST_KEY_REQUIRED");
        }
        return repository.runByPlanAndRequestKey(plan.id(), normalizedRequestKey)
                .map(run -> detail(run, true))
                .orElseGet(() -> createRun(
                        plan,
                        null,
                        normalizedRequestKey,
                        normalizedTriggerType,
                        boundedNullableText(sourceEventId, 256),
                        triggerSummary
                ));
    }

    @Transactional(readOnly = true)
    public PageResponse<ExecutionRunSummaryResponse> runs(ExecutionRunPageRequest request) {
        ExecutionRunQuery query = normalizeQuery(request.toQuery());
        List<ExecutionRun> runs = repository.runs(query);
        Map<UUID, Integer> nodeCounts = runs.stream()
                .collect(Collectors.toMap(
                        ExecutionRun::id,
                        run -> repository.nodeRuns(run.id()).size()
                ));
        List<ExecutionRunSummaryResponse> items = runs.stream()
                .map(run -> toSummary(run, nodeCounts.getOrDefault(run.id(), 0)))
                .toList();
        return PageResponse.of(items, request.getIndex(), request.getSize(), repository.countRuns(query));
    }

    @Transactional(readOnly = true)
    public ExecutionRunDetailResponse run(UUID id) {
        return detail(requireRun(id), false);
    }

    /**
     * Cancels only the WP9 control-plane records in this slice.
     *
     * <p>M3B does not call WP6/WP7 runner cancel ports because dispatch is not wired yet. Terminal runs are returned
     * unchanged so client retries remain idempotent, while queued or running node records are closed with bounded
     * cancellation metadata and no runner output.</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ExecutionRunDetailResponse cancelRun(UUID id) {
        ExecutionRun run = requireRun(id);
        if (TERMINAL_RUN_STATUSES.contains(run.status())) {
            return detail(run, false);
        }
        Instant now = Instant.now();
        List<ExecutionNodeRun> existingNodeRuns = repository.nodeRuns(run.id());
        List<ExecutionNodeRun> canceledNodeRuns = existingNodeRuns.stream()
                .filter(nodeRun -> CANCELABLE_NODE_STATUSES.contains(nodeRun.status()))
                .map(nodeRun -> canceledNodeRun(nodeRun, now))
                .toList();
        ExecutionRun canceled = new ExecutionRun(
                run.id(),
                run.planId(),
                run.projectId(),
                "CANCELED",
                run.triggerType(),
                run.requestKey(),
                run.sourceEventId(),
                run.attempt(),
                run.traceId(),
                mergedSummary(run.resultSummaryJson(), Map.of(
                        "canceled", true,
                        "canceledNodeCount", canceledNodeRuns.size(),
                        "runnerCancelAttempted", false,
                        "runnerDispatched", false
                )),
                "EXECUTION_RUN_CANCELED",
                "Execution run canceled before runner dispatch",
                run.createdBy(),
                run.startedAt(),
                now,
                run.createdAt(),
                now
        );
        repository.updateRun(canceled);
        repository.updateNodeRuns(canceledNodeRuns);
        auditRun(canceled, "execution.run.canceled", "SUCCESS", Map.of(
                "status", canceled.status(),
                "canceledNodeCount", canceledNodeRuns.size(),
                "runnerCancelAttempted", false
        ));
        return detail(requireRun(id), false);
    }

    /**
     * Re-queues failed control-plane node attempts without creating a second execution run.
     *
     * <p>The original failed attempts are retained as immutable evidence. New attempts are inserted only for latest
     * failed, timed-out or blocked nodes, and a run already queued by a retry is returned unchanged to avoid duplicate
     * attempts from repeated button clicks.</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ExecutionRunDetailResponse retryRun(UUID id) {
        ExecutionRun run = requireRun(id);
        if (retryAlreadyQueued(run)) {
            return detail(run, false);
        }
        if (!RETRYABLE_RUN_STATUSES.contains(run.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_RUN_NOT_RETRYABLE");
        }
        Instant now = Instant.now();
        List<ExecutionNodeRun> existingNodeRuns = repository.nodeRuns(run.id());
        List<ExecutionPlanNode> planNodes = repository.planNodes(run.planId());
        List<ExecutionNodeRun> retryableNodeRuns = latestNodeRuns(existingNodeRuns).stream()
                .filter(nodeRun -> RETRYABLE_NODE_STATUSES.contains(nodeRun.status()))
                .toList();
        if (retryableNodeRuns.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_RETRY_NODE_NOT_FOUND");
        }
        Map<UUID, ExecutionPlanNode> planNodeById = planNodes.stream()
                .collect(Collectors.toMap(ExecutionPlanNode::id, Function.identity()));
        Set<String> retryNodeKeys = retryableNodeRuns.stream()
                .map(nodeRun -> planNodeById.get(nodeRun.planNodeId()))
                .filter(Objects::nonNull)
                .map(ExecutionPlanNode::nodeKey)
                .collect(Collectors.toSet());
        List<ExecutionNodeRun> retryNodeRuns = retryableNodeRuns.stream()
                .map(nodeRun -> retryNodeRun(nodeRun, planNodeById.get(nodeRun.planNodeId()), retryNodeKeys, now))
                .toList();
        ExecutionRun retried = new ExecutionRun(
                run.id(),
                run.planId(),
                run.projectId(),
                "QUEUED",
                "RETRY",
                run.requestKey(),
                run.sourceEventId(),
                run.attempt() + 1,
                TraceContext.getOrCreateTraceId(),
                mergedSummary(run.resultSummaryJson(), Map.of(
                        "retryInFlight", true,
                        "retryAttempt", run.attempt() + 1,
                        "retryNodeCount", retryNodeRuns.size(),
                        "schedulerClaimCreated", false,
                        "runnerDispatched", false
                )),
                null,
                null,
                run.createdBy(),
                null,
                null,
                run.createdAt(),
                now
        );
        repository.updateRun(retried);
        repository.insertNodeRuns(retryNodeRuns);
        auditRun(retried, "execution.run.retried", "SUCCESS", Map.of(
                "status", retried.status(),
                "attempt", retried.attempt(),
                "retryNodeCount", retryNodeRuns.size(),
                "runnerDispatched", false
        ));
        return detail(requireRun(id), false);
    }

    /**
     * Claims one queued node for an internal scheduler worker without dispatching a runner.
     *
     * <p>The claim row is inserted before the node moves to RUNNING, and the active-claim unique index prevents two
     * workers from owning the same node. If the node changed state between selection and update, the claim is released
     * and the next queued node is attempted.</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public Optional<ExecutionQueueClaimResponse> claimNextQueuedNode(String workerId) {
        String normalizedWorkerId = boundedNullableText(workerId, 128);
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

    /**
     * Completes a claimed node and immediately aggregates the enclosing run.
     *
     * <p>Only sanitized result summary keys are copied into WP9 storage. Raw stdout/stderr, request/response bodies,
     * variables and secret-looking fields are deliberately dropped because WP9 stores execution evidence summaries, not
     * runner payloads.</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ExecutionRunDetailResponse completeClaimedNodeRun(CompleteExecutionNodeRunCommand command) {
        if (command == null || command.nodeRunId() == null || !StringUtils.hasText(command.claimToken())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_QUEUE_CLAIM_REQUIRED");
        }
        String targetStatus = Optional.ofNullable(boundedNullableText(command.status(), 32))
                .map(status -> status.toUpperCase(Locale.ROOT))
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
                mergedSummary(nodeRun.resultSummaryJson(), sanitizedCompletionSummary(command, now)),
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

    /**
     * Dispatches a claimed API_TEST node through the WP6 application service and closes the WP9 node with the
     * sanitized WP6 run outcome.
     *
     * <p>The runtime baseUrl and secretRefs are passed only to WP6. WP9 stores the WP6 run ID, host/digest evidence and
     * aggregate counts, keeping raw target URLs, secret references and runner payloads out of execution summaries.</p>
     */
    public ExecutionRunDetailResponse dispatchClaimedApiTestNodeRun(DispatchExecutionNodeRunCommand command) {
        if (command == null || command.nodeRunId() == null || !StringUtils.hasText(command.claimToken())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_QUEUE_CLAIM_REQUIRED");
        }
        ApiTestDispatchPreparation preparation = inExecutionTransaction(() -> prepareApiTestDispatch(command));
        if (preparation.replayResponse() != null) {
            return preparation.replayResponse();
        }
        ApiAutomationRunDetailResponse wp6Run = apiAutomationService.createRun(new CreateApiAutomationRunCommand(
                preparation.bundleId(),
                preparation.environmentId(),
                preparation.baseUrl(),
                preparation.caseIds(),
                preparation.timeoutSeconds(),
                preparation.secretRefs()
        ));
        return inExecutionTransaction(() -> completeDispatchedApiTestNodeRun(preparation, wp6Run));
    }

    private ApiTestDispatchPreparation prepareApiTestDispatch(DispatchExecutionNodeRunCommand command) {
        ExecutionQueueClaim claim = repository.queueClaimByToken(command.claimToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_INVALID"));
        if (!command.nodeRunId().equals(claim.nodeRunId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_NODE_MISMATCH");
        }
        ExecutionNodeRun nodeRun = repository.nodeRun(command.nodeRunId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行节点运行不存在"));
        Instant now = Instant.now();
        if ("COMPLETED".equals(claim.status()) && COMPLETABLE_NODE_STATUSES.contains(nodeRun.status())) {
            return ApiTestDispatchPreparation.replay(detail(requireRun(nodeRun.runId()), false));
        }
        if (!"CLAIMED".equals(claim.status()) || !claim.expiresAt().isAfter(now) || !"RUNNING".equals(nodeRun.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_NOT_ACTIVE");
        }

        ExecutionRun run = requireRun(nodeRun.runId());
        ExecutionPlanNode planNode = repository.planNodes(run.planId()).stream()
                .filter(node -> node.id().equals(nodeRun.planNodeId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行计划节点不存在"));
        if (!"API_TEST".equals(planNode.nodeType()) || !"WP6_API".equals(nodeRun.runnerType())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_NODE_DISPATCH_UNSUPPORTED");
        }
        Map<String, Object> planInput = readMap(planNode.inputSummaryJson());
        ResolvedDispatchTarget target = resolvedDispatchTarget(command, planInput, run.projectId());
        List<String> secretRefs = dispatchSecretRefs(command, planInput);
        renewClaimForDispatch(claim, planNode, now);
        return new ApiTestDispatchPreparation(
                nodeRun.id(),
                command.claimToken(),
                apiAutomationBundleId(planNode),
                dispatchEnvironmentId(command, target),
                target.baseUrl(),
                target.baseUrlSource(),
                target.baseUrlRef(),
                dispatchCaseIds(command, planNode),
                planNode.timeoutSeconds(),
                secretRefs,
                command.caseIds() != null && !command.caseIds().isEmpty(),
                command.secretRefs() != null && !command.secretRefs().isEmpty(),
                null
        );
    }

    private ExecutionRunDetailResponse completeDispatchedApiTestNodeRun(
            ApiTestDispatchPreparation preparation,
            ApiAutomationRunDetailResponse wp6Run
    ) {
        ExecutionQueueClaim claim = repository.queueClaimByToken(preparation.claimToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_INVALID"));
        if (!preparation.nodeRunId().equals(claim.nodeRunId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_NODE_MISMATCH");
        }
        ExecutionNodeRun nodeRun = repository.nodeRun(preparation.nodeRunId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行节点运行不存在"));
        Instant completedAt = Instant.now();
        if ("COMPLETED".equals(claim.status()) && COMPLETABLE_NODE_STATUSES.contains(nodeRun.status())) {
            return detail(requireRun(nodeRun.runId()), false);
        }
        if (!"CLAIMED".equals(claim.status())
                || !claim.expiresAt().isAfter(completedAt)
                || !"RUNNING".equals(nodeRun.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_NOT_ACTIVE");
        }
        String targetStatus = wp9StatusFromWp6(wp6Run.run().status());
        ExecutionNodeRun completed = new ExecutionNodeRun(
                nodeRun.id(),
                nodeRun.runId(),
                nodeRun.planNodeId(),
                targetStatus,
                nodeRun.attempt(),
                nodeRun.runnerType(),
                wp6Run.run().id().toString(),
                terminalErrorCode(targetStatus, wp6DispatchErrorCode(targetStatus, wp6Run.run())),
                terminalErrorSummary(targetStatus, wp6Run.run().errorSummary()),
                mergedSummary(nodeRun.resultSummaryJson(), wp6DispatchSummary(wp6Run, preparation, completedAt)),
                completedAt,
                nodeRun.queuedAt(),
                nodeRun.startedAt(),
                completedAt,
                nodeRun.createdAt(),
                completedAt
        );
        repository.updateNodeRuns(List.of(completed));
        repository.updateQueueClaim(new ExecutionQueueClaim(
                claim.id(),
                claim.nodeRunId(),
                claim.claimToken(),
                claim.workerId(),
                claim.claimedAt(),
                completedAt,
                claim.expiresAt(),
                "COMPLETED",
                claim.createdAt(),
                completedAt
        ));
        return aggregateRunAfterNodeCompletion(completed.runId(), completedAt);
    }

    /**
     * Extends an active queue claim lease and records the node heartbeat used by recovery.
     *
     * <p>The claim token stays opaque and is never copied into node summaries. A heartbeat is accepted only while the
     * claim is still active and the node remains RUNNING; completed or recovered claims must not be revived by retries
     * from an old worker.</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ExecutionQueueClaimResponse heartbeatQueueClaim(HeartbeatExecutionQueueClaimCommand command) {
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
                mergedSummary(nodeRun.resultSummaryJson(), Map.of(
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

    /**
     * Recovers expired queue claims and stale RUNNING nodes without starting a scheduler thread.
     *
     * <p>Expired claims are first marked EXPIRED to release the active-claim unique index. The node is then either
     * re-queued for another worker when its node timeout has not elapsed, or closed as TIMEOUT and aggregated into the
     * enclosing run. This preserves at-most-one active claim while making stalled work observable and retryable.</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ExecutionQueueRecoveryResponse recoverExpiredQueueClaims() {
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

    public String runProjectScopeId(UUID id) {
        return repository.runProjectScopeId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行运行不存在"));
    }

    private ExecutionRunDetailResponse createRun(
            ExecutionPlan plan,
            TriggerExecutionRunCommand command,
            String requestKey,
            String triggerType,
            String sourceEventId,
            Map<String, Object> triggerSummary
    ) {
        Instant now = Instant.now();
        List<ExecutionPlanNode> planNodes = repository.planNodes(plan.id());
        ExecutionDagValidationResult validation = dagValidator.validate(
                plan.id(),
                plan.projectId(),
                toDagCommand(planNodes),
                now
        );
        if (!validation.valid()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_DAG_INVALID: "
                    + validation.issues().stream()
                    .findFirst()
                    .map(com.songhg.veri.agent.execution.application.view.ExecutionValidationIssueResponse::code)
                    .orElse("UNKNOWN"));
        }
        UUID runId = UUID.randomUUID();
        String triggerReason = boundedNullableText(command == null ? null : command.reason(), 256);
        String traceId = TraceContext.getOrCreateTraceId();
        List<ExecutionPlanNode> orderedPlanNodes = orderedPersistedNodes(planNodes, validation.nodes());
        long queuedNodeCount = orderedPlanNodes.stream()
                .filter(node -> node.dependencyKeys().isEmpty())
                .count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("nodeCount", orderedPlanNodes.size());
        summary.put("queuedNodeCount", queuedNodeCount);
        summary.put("pendingNodeCount", orderedPlanNodes.size() - queuedNodeCount);
        summary.put("dagDigest", validation.dagDigest());
        summary.put("manualReasonPresent", "MANUAL".equals(triggerType) && StringUtils.hasText(triggerReason));
        summary.put("variablesAccepted", command != null && command.variables() != null
                && !command.variables().isEmpty());
        summary.put("schedulerClaimCreated", false);
        summary.put("runnerDispatched", false);
        if (triggerSummary != null && !triggerSummary.isEmpty()) {
            summary.putAll(triggerSummary);
        }
        ExecutionRun run = new ExecutionRun(
                runId,
                plan.id(),
                plan.projectId(),
                "QUEUED",
                triggerType,
                requestKey,
                sourceEventId,
                1,
                traceId,
                json(summary),
                null,
                null,
                actorResolver.currentActor(),
                null,
                null,
                now,
                now
        );
        List<ExecutionNodeRun> nodeRuns = orderedPlanNodes.stream()
                .map(node -> toInitialNodeRun(run.id(), node, now))
                .toList();
        boolean inserted = repository.insertRun(run);
        if (!inserted) {
            return repository.runByPlanAndRequestKey(plan.id(), requestKey)
                    .map(existing -> detail(existing, true))
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "EXECUTION_DUPLICATE_TRIGGER"));
        }
        repository.insertNodeRuns(nodeRuns);
        auditRun(run, "execution.run.created", "SUCCESS", Map.of(
                "status", run.status(),
                "triggerType", run.triggerType(),
                "nodeCount", nodeRuns.size(),
                "requestKeyPresent", StringUtils.hasText(requestKey),
                "runnerDispatched", false
        ));
        return detail(run, false, nodeRuns, orderedPlanNodes);
    }

    private List<ExecutionPlanNode> orderedPersistedNodes(
            List<ExecutionPlanNode> persistedNodes,
            List<ExecutionPlanNode> validatedNodes
    ) {
        Map<String, ExecutionPlanNode> persistedByKey = persistedNodes.stream()
                .collect(Collectors.toMap(ExecutionPlanNode::nodeKey, Function.identity()));
        List<ExecutionPlanNode> orderedNodes = validatedNodes.stream()
                .map(node -> persistedByKey.get(node.nodeKey()))
                .toList();
        if (orderedNodes.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_DAG_INVALID: EXECUTION_DAG_NODE_MISSING");
        }
        return orderedNodes;
    }

    private ExecutionNodeRun toInitialNodeRun(UUID runId, ExecutionPlanNode node, Instant now) {
        return new ExecutionNodeRun(
                UUID.randomUUID(),
                runId,
                node.id(),
                node.dependencyKeys().isEmpty() ? "QUEUED" : "PENDING",
                1,
                runnerType(node.nodeType()),
                null,
                null,
                null,
                json(Map.of(
                        "planNodeKey", node.nodeKey(),
                        "dependencyCount", node.dependencyKeys().size(),
                        "dispatchReady", false
                )),
                null,
                node.dependencyKeys().isEmpty() ? now : null,
                null,
                null,
                now,
                now
        );
    }

    private ExecutionNodeRun canceledNodeRun(ExecutionNodeRun nodeRun, Instant now) {
        return new ExecutionNodeRun(
                nodeRun.id(),
                nodeRun.runId(),
                nodeRun.planNodeId(),
                "CANCELED",
                nodeRun.attempt(),
                nodeRun.runnerType(),
                nodeRun.externalRunId(),
                "EXECUTION_RUN_CANCELED",
                "Execution node canceled before runner dispatch",
                mergedSummary(nodeRun.resultSummaryJson(), Map.of(
                        "canceled", true,
                        "runnerCancelAttempted", false,
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

    private ExecutionNodeRun retryNodeRun(
            ExecutionNodeRun previous,
            ExecutionPlanNode planNode,
            Set<String> retryNodeKeys,
            Instant now
    ) {
        boolean waitsForRetryDependency = planNode != null
                && planNode.dependencyKeys().stream().anyMatch(retryNodeKeys::contains);
        String status = waitsForRetryDependency ? "PENDING" : "QUEUED";
        return new ExecutionNodeRun(
                UUID.randomUUID(),
                previous.runId(),
                previous.planNodeId(),
                status,
                previous.attempt() + 1,
                previous.runnerType(),
                null,
                null,
                null,
                json(Map.ofEntries(
                        Map.entry("retryOfNodeRunId", previous.id().toString()),
                        Map.entry("previousAttempt", previous.attempt()),
                        Map.entry("retryAttempt", previous.attempt() + 1),
                        Map.entry("planNodeKey", planNode == null ? "" : planNode.nodeKey()),
                        Map.entry("dependencyCount", planNode == null ? 0 : planNode.dependencyKeys().size()),
                        Map.entry("dispatchReady", false),
                        Map.entry("runnerDispatched", false)
                )),
                null,
                "QUEUED".equals(status) ? now : null,
                null,
                null,
                now,
                now
        );
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
                mergedSummary(candidate.resultSummaryJson(), Map.of(
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
                    mergedSummary(run.resultSummaryJson(), Map.of(
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
                mergedSummary(nodeRun.resultSummaryJson(), Map.of(
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
                mergedSummary(nodeRun.resultSummaryJson(), Map.of(
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

    private ExecutionRunDetailResponse aggregateRunAfterNodeCompletion(UUID runId, Instant now) {
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
                    "summary", readMap(aggregated.resultSummaryJson())
            ));
        }
        return detail(requireRun(runId), false);
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
                    mergedSummary(nodeRun.resultSummaryJson(), Map.of(
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
                    mergedSummary(nodeRun.resultSummaryJson(), Map.of(
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
                mergedSummary(run.resultSummaryJson(), Map.ofEntries(
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

    private List<ExecutionNodeRun> latestNodeRuns(List<ExecutionNodeRun> nodeRuns) {
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

    private boolean retryAlreadyQueued(ExecutionRun run) {
        return "QUEUED".equals(run.status())
                && "RETRY".equals(run.triggerType())
                && Boolean.TRUE.equals(readMap(run.resultSummaryJson()).get("retryInFlight"));
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
            return boundedSummaryText(redactSensitiveText(text));
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
        return boundedSummaryText(redactSensitiveText(String.valueOf(value)));
    }

    private String boundedSummaryText(String value) {
        if (value == null || value.length() <= MAX_RESULT_SUMMARY_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_RESULT_SUMMARY_TEXT_LENGTH - 3) + "...";
    }

    private String redactSensitiveText(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String redacted = value;
        for (Pattern pattern : SENSITIVE_TEXT_PATTERNS) {
            redacted = pattern.matcher(redacted).replaceAll("[REDACTED]");
        }
        return redacted;
    }

    private boolean containsSensitiveText(String value) {
        return StringUtils.hasText(value)
                && SENSITIVE_TEXT_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(value).find());
    }

    private String terminalErrorCode(String status, String requestedErrorCode) {
        if (SUCCESS_NODE_STATUSES.contains(status)) {
            return null;
        }
        if (StringUtils.hasText(requestedErrorCode)) {
            String normalized = boundedNullableText(requestedErrorCode, 64);
            if (!containsSensitiveText(normalized) && ERROR_CODE_PATTERN.matcher(normalized).matches()) {
                return normalized;
            }
        }
        return switch (status) {
            case "TIMEOUT" -> "EXECUTION_RUN_TIMEOUT";
            case "BLOCKED" -> "EXECUTION_DEPENDENCY_BLOCKED";
            default -> "EXECUTION_NODE_FAILED";
        };
    }

    private String terminalErrorSummary(String status, String requestedErrorSummary) {
        if (SUCCESS_NODE_STATUSES.contains(status)) {
            return null;
        }
        if (StringUtils.hasText(requestedErrorSummary)) {
            return boundedNullableText(redactSensitiveText(requestedErrorSummary), 512);
        }
        return switch (status) {
            case "TIMEOUT" -> "Execution node timed out";
            case "BLOCKED" -> "Execution node blocked";
            default -> "Execution node failed";
        };
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

    private void renewClaimForDispatch(ExecutionQueueClaim claim, ExecutionPlanNode planNode, Instant now) {
        int timeoutSeconds = planNode.timeoutSeconds() <= 0
                ? properties.effectiveDefaultRunTimeoutSeconds()
                : planNode.timeoutSeconds();
        int leaseSeconds = timeoutSeconds + properties.effectiveNodeHeartbeatTimeoutSeconds();
        ExecutionQueueClaim renewed = new ExecutionQueueClaim(
                claim.id(),
                claim.nodeRunId(),
                claim.claimToken(),
                claim.workerId(),
                claim.claimedAt(),
                now,
                now.plusSeconds(leaseSeconds),
                "CLAIMED",
                claim.createdAt(),
                now
        );
        if (!repository.updateQueueClaimIfStatus(renewed, "CLAIMED")) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_NOT_ACTIVE");
        }
    }

    private UUID apiAutomationBundleId(ExecutionPlanNode planNode) {
        Object bundleId = readMap(planNode.inputSummaryJson()).get("apiAutomationBundleId");
        try {
            return UUID.fromString(String.valueOf(bundleId));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_RESOURCE_REQUIRED");
        }
    }

    private List<UUID> dispatchCaseIds(DispatchExecutionNodeRunCommand command, ExecutionPlanNode planNode) {
        List<UUID> commandCaseIds = normalizedUuidList(command.caseIds());
        if (!commandCaseIds.isEmpty()) {
            return commandCaseIds;
        }
        Object planCaseIds = readMap(planNode.inputSummaryJson()).get("caseIds");
        if (!(planCaseIds instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<UUID> normalized = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Object value : iterable) {
            if (value == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(String.valueOf(value));
                if (seen.add(id)) {
                    normalized.add(id);
                }
            } catch (RuntimeException exception) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_DISPATCH_CASE_IDS_INVALID");
            }
        }
        return normalized;
    }

    private List<UUID> normalizedUuidList(List<UUID> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<UUID> normalized = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (UUID value : values) {
            if (value != null && seen.add(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private List<String> normalizedDispatchSecretRefs(List<String> secretRefs) {
        if (secretRefs == null || secretRefs.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String secretRef : secretRefs) {
            String bounded = boundedNullableText(secretRef, 256);
            if (StringUtils.hasText(bounded) && seen.add(bounded)) {
                normalized.add(bounded);
            }
        }
        return normalized;
    }

    /**
     * Resolves the runner target for M4B dispatch. Explicit runtime baseUrl wins; otherwise `baseUrlRef=env:<key>`
     * resolves against WP1 environment metadata and must stay inside the execution plan project.
     */
    private ResolvedDispatchTarget resolvedDispatchTarget(
            DispatchExecutionNodeRunCommand command,
            Map<String, Object> planInput,
            String projectId
    ) {
        String runtimeBaseUrl = boundedNullableText(command.baseUrl(), 512);
        if (StringUtils.hasText(runtimeBaseUrl)) {
            return new ResolvedDispatchTarget(runtimeBaseUrl, "REQUEST_BASE_URL", null, null);
        }
        boolean requestBaseUrlRefProvided = StringUtils.hasText(command.baseUrlRef());
        String baseUrlRef = firstText(command.baseUrlRef(), planInput.get("baseUrlRef"));
        if (!StringUtils.hasText(baseUrlRef)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_DISPATCH_BASE_URL_REQUIRED");
        }
        String normalizedRef = boundedNullableText(baseUrlRef, 128);
        if (!normalizedRef.startsWith("env:")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_DISPATCH_BASE_URL_REF_UNSUPPORTED");
        }
        String environmentKey = normalizedRef.substring("env:".length()).trim();
        if (!StringUtils.hasText(environmentKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_DISPATCH_BASE_URL_REF_INVALID");
        }
        UUID projectUuid = executionProjectUuid(projectId);
        EnvironmentRuntimeRef environment = requireEnvironmentRuntimeRef(environmentKey, projectUuid);
        if (!projectUuid.equals(environment.projectId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_DISPATCH_ENVIRONMENT_SCOPE_DENIED");
        }
        if (!"ENABLED".equals(environment.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_DISPATCH_ENVIRONMENT_DISABLED");
        }
        if (!StringUtils.hasText(environment.apiBaseUrl())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_DISPATCH_BASE_URL_REF_EMPTY");
        }
        return new ResolvedDispatchTarget(
                boundedNullableText(environment.apiBaseUrl(), 512),
                requestBaseUrlRefProvided ? "REQUEST_BASE_URL_REF" : "PLAN_BASE_URL_REF",
                normalizedRef,
                environment.code()
        );
    }

    private UUID executionProjectUuid(String projectId) {
        try {
            return UUID.fromString(projectId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_DISPATCH_PROJECT_ID_INVALID");
        }
    }

    private EnvironmentRuntimeRef requireEnvironmentRuntimeRef(String environmentKey, UUID projectId) {
        if (managementStore == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_DISPATCH_ENVIRONMENT_RESOLVER_UNAVAILABLE");
        }
        EnvironmentRuntimeRef environment = managementStore.findEnvironmentRuntimeRef(
                ManagementStoreParams.of(
                        "keyword", boundedNullableText(environmentKey, 128),
                        "projectId", projectId
                )
        );
        if (environment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "EXECUTION_DISPATCH_ENVIRONMENT_NOT_FOUND");
        }
        return environment;
    }

    /**
     * Chooses runtime secret references for WP6 without exposing them in WP9 responses. Request values override plan
     * defaults; plan defaults must use `runtimeSecretRefs` because the public `secretRefs` input remains masked.
     */
    private List<String> dispatchSecretRefs(DispatchExecutionNodeRunCommand command, Map<String, Object> planInput) {
        List<String> commandSecretRefs = normalizedDispatchSecretRefs(command.secretRefs());
        if (!commandSecretRefs.isEmpty()) {
            return commandSecretRefs;
        }
        return normalizedDispatchSecretRefs(stringList(planInput.get("runtimeSecretRefs")));
    }

    private String dispatchEnvironmentId(DispatchExecutionNodeRunCommand command, ResolvedDispatchTarget target) {
        String environmentId = boundedNullableText(command.environmentId(), 128);
        if (StringUtils.hasText(environmentId)) {
            return environmentId;
        }
        return target.environmentKey();
    }

    private String firstText(String explicit, Object fallback) {
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        if (fallback instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        return null;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : iterable) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private String wp9StatusFromWp6(String wp6Status) {
        String normalized = StringUtils.hasText(wp6Status) ? wp6Status.trim().toUpperCase(Locale.ROOT) : "";
        return switch (normalized) {
            case "PASSED" -> "SUCCEEDED";
            case "TIMEOUT" -> "TIMEOUT";
            case "BLOCKED" -> "BLOCKED";
            default -> "FAILED";
        };
    }

    private String wp6DispatchErrorCode(String wp9Status, ApiAutomationRunResponse wp6Run) {
        if (SUCCESS_NODE_STATUSES.contains(wp9Status)) {
            return null;
        }
        if (StringUtils.hasText(wp6Run.errorCode())) {
            return wp6Run.errorCode();
        }
        return switch (wp9Status) {
            case "TIMEOUT" -> "EXECUTION_WP6_RUN_TIMEOUT";
            case "BLOCKED" -> "EXECUTION_WP6_RUN_BLOCKED";
            default -> "EXECUTION_WP6_RUN_FAILED";
        };
    }

    private Map<String, Object> wp6DispatchSummary(
            ApiAutomationRunDetailResponse wp6Run,
            ApiTestDispatchPreparation preparation,
            Instant completedAt
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("completedStatus", wp9StatusFromWp6(wp6Run.run().status()));
        summary.put("completedAt", completedAt.toString());
        summary.put("runnerDispatched", true);
        summary.put("wp6DispatchReady", true);
        summary.put("wp6RunId", wp6Run.run().id().toString());
        summary.put("wp6Status", wp6Run.run().status());
        summary.put("wp6RunnerMode", wp6Run.run().runnerMode());
        summary.put("wp6CaseCount", wp6Run.run().caseCount());
        summary.put("wp6ResultCount", wp6Run.results().size());
        summary.put("wp6ResultCounts", wp6ResultCounts(wp6Run.results()));
        summary.put("wp6BaseUrlHost", wp6Run.run().baseUrlHost());
        summary.put("wp6BaseUrlDigest", wp6Run.run().baseUrlDigest());
        summary.put("wp6TraceId", wp6Run.run().traceId());
        summary.put("baseUrlSource", preparation.baseUrlSource());
        summary.put("baseUrlRefDigest", StringUtils.hasText(preparation.baseUrlRef())
                ? "sha256:" + sha256(preparation.baseUrlRef())
                : null);
        summary.put("runtimeCaseIdsProvided", preparation.runtimeCaseIdsProvided());
        summary.put("runtimeSecretRefsProvided", preparation.runtimeSecretRefsProvided());
        summary.put("runtimeSecretRefCount", preparation.secretRefs().size());
        summary.put("runtimeSecretRefDigests", preparation.secretRefs().stream()
                .map(secretRef -> "sha256:" + sha256(secretRef))
                .toList());
        summary.put("rawBaseUrlStored", false);
        summary.put("secretRefsStored", false);
        summary.put("rawOutputStored", false);
        summary.put("requestResponseStored", false);
        return summary;
    }

    private Map<String, Integer> wp6ResultCounts(List<ApiAutomationRunResultResponse> results) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (results == null) {
            return counts;
        }
        results.stream()
                .filter(Objects::nonNull)
                .forEach(result -> counts.merge(result.status(), 1, Integer::sum));
        return counts;
    }

    private boolean runnerDispatched(List<ExecutionNodeRun> nodeRuns) {
        return nodeRuns.stream().anyMatch(nodeRun -> StringUtils.hasText(nodeRun.externalRunId())
                || Boolean.TRUE.equals(readMap(nodeRun.resultSummaryJson()).get("runnerDispatched")));
    }

    private <T> T inExecutionTransaction(Supplier<T> action) {
        if (transactionTemplate == null) {
            return action.get();
        }
        return transactionTemplate.execute(ignored -> action.get());
    }

    private ExecutionRunDetailResponse detail(ExecutionRun run, boolean idempotentReplay) {
        return detail(run, idempotentReplay, repository.nodeRuns(run.id()), repository.planNodes(run.planId()));
    }

    private ExecutionRunDetailResponse detail(
            ExecutionRun run,
            boolean idempotentReplay,
            List<ExecutionNodeRun> nodeRuns,
            List<ExecutionPlanNode> planNodes
    ) {
        Map<UUID, ExecutionPlanNode> nodeById = planNodes.stream()
                .collect(Collectors.toMap(ExecutionPlanNode::id, Function.identity()));
        return new ExecutionRunDetailResponse(
                run.id(),
                run.planId(),
                run.projectId(),
                run.status(),
                run.triggerType(),
                run.requestKey(),
                run.sourceEventId(),
                run.attempt(),
                run.traceId(),
                readMap(run.resultSummaryJson()),
                run.errorCode(),
                run.errorSummary(),
                nodeRuns.stream()
                        .map(nodeRun -> toNodeRunResponse(nodeRun, nodeById.get(nodeRun.planNodeId())))
                        .toList(),
                idempotentReplay,
                run.createdBy(),
                run.startedAt(),
                run.finishedAt(),
                run.createdAt(),
                run.updatedAt()
        );
    }

    private ExecutionRunSummaryResponse toSummary(ExecutionRun run, int nodeCount) {
        return new ExecutionRunSummaryResponse(
                run.id(),
                run.planId(),
                run.projectId(),
                run.status(),
                run.triggerType(),
                run.requestKey(),
                run.attempt(),
                run.traceId(),
                readMap(run.resultSummaryJson()),
                nodeCount,
                run.createdBy(),
                run.startedAt(),
                run.finishedAt(),
                run.createdAt(),
                run.updatedAt()
        );
    }

    private ExecutionNodeRunResponse toNodeRunResponse(ExecutionNodeRun nodeRun, ExecutionPlanNode planNode) {
        return new ExecutionNodeRunResponse(
                nodeRun.id(),
                nodeRun.planNodeId(),
                planNode == null ? null : planNode.nodeKey(),
                planNode == null ? null : planNode.nodeType(),
                nodeRun.status(),
                nodeRun.attempt(),
                nodeRun.runnerType(),
                nodeRun.externalRunId(),
                nodeRun.errorCode(),
                nodeRun.errorSummary(),
                readMap(nodeRun.resultSummaryJson()),
                nodeRun.heartbeatAt(),
                nodeRun.queuedAt(),
                nodeRun.startedAt(),
                nodeRun.finishedAt(),
                nodeRun.createdAt(),
                nodeRun.updatedAt()
        );
    }

    private ExecutionPlan requirePlan(UUID id) {
        return repository.plan(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行计划不存在"));
    }

    private ExecutionRun requireRun(UUID id) {
        return repository.run(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行运行不存在"));
    }

    private ExecutionRunQuery normalizeQuery(ExecutionRunQuery query) {
        String projectId = query.projectId();
        if (StringUtils.hasText(projectId)) {
            projectId = contextClient.projectContext(projectId).resourceId();
        }
        String status = query.status();
        if (StringUtils.hasText(status) && !RUN_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_RUN_STATUS_INVALID");
        }
        return new ExecutionRunQuery(projectId, query.planId(), status, query.limit(), query.offset());
    }

    private com.songhg.veri.agent.execution.application.command.ExecutionDagCommand toDagCommand(
            List<ExecutionPlanNode> nodes
    ) {
        return new com.songhg.veri.agent.execution.application.command.ExecutionDagCommand(nodes.stream()
                .map(node -> new com.songhg.veri.agent.execution.application.command.ExecutionDagNodeCommand(
                        node.nodeKey(),
                        node.nodeType(),
                        node.dependencyKeys(),
                        readMap(node.inputSummaryJson()),
                        node.timeoutSeconds(),
                        node.failurePolicy(),
                        readMap(node.retryPolicyJson())
                ))
                .toList());
    }

    private String runnerType(String nodeType) {
        return switch (nodeType) {
            case "API_TEST" -> "WP6_API";
            case "UI_TEST" -> "WP7_UI";
            case "SETUP", "VERIFY", "CLEANUP" -> "UTILITY";
            case "REPORT_HANDOFF" -> "REPORT";
            default -> "CONTROL";
        };
    }

    private void auditRun(ExecutionRun run, String action, String result, Map<String, Object> afterJson) {
        contextClient.writeAuditEvent(action, "EXECUTION_RUN", run.id().toString(), run.projectId(), result, afterJson);
    }

    private String boundedNullableText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of("unreadable", true);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "EXECUTION_JSON_INVALID");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String mergedSummary(String existingJson, Map<String, Object> overrides) {
        Map<String, Object> summary = new LinkedHashMap<>(readMap(existingJson));
        summary.putAll(overrides);
        return json(summary);
    }

    private record DependencyState(boolean ready, String blockedByDependencyKey) {
    }

    private record ApiTestDispatchPreparation(
            UUID nodeRunId,
            String claimToken,
            UUID bundleId,
            String environmentId,
            String baseUrl,
            String baseUrlSource,
            String baseUrlRef,
            List<UUID> caseIds,
            int timeoutSeconds,
            List<String> secretRefs,
            boolean runtimeCaseIdsProvided,
            boolean runtimeSecretRefsProvided,
            ExecutionRunDetailResponse replayResponse
    ) {
        private static ApiTestDispatchPreparation replay(ExecutionRunDetailResponse replayResponse) {
            return new ApiTestDispatchPreparation(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    0,
                    List.of(),
                    false,
                    false,
                    replayResponse
            );
        }
    }

    private record ResolvedDispatchTarget(String baseUrl, String baseUrlSource, String baseUrlRef, String environmentKey) {
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
