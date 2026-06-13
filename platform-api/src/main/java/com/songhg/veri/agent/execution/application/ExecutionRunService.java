package com.songhg.veri.agent.execution.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.execution.application.command.TriggerExecutionRunCommand;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.query.ExecutionRunPageRequest;
import com.songhg.veri.agent.execution.application.query.ExecutionRunQuery;
import com.songhg.veri.agent.execution.application.view.ExecutionNodeRunResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunSummaryResponse;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ExecutionRepository repository;
    private final ExecutionDagValidator dagValidator;
    private final ExecutionPlatformContextClient contextClient;
    private final ExecutionActorResolver actorResolver;
    private final ObjectMapper objectMapper;

    public ExecutionRunService(
            ExecutionRepository repository,
            ExecutionDagValidator dagValidator,
            ExecutionPlatformContextClient contextClient,
            ExecutionActorResolver actorResolver,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.dagValidator = dagValidator;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.objectMapper = objectMapper;
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
                    .orElseGet(() -> createManualRun(plan, command, requestKey));
        }
        return createManualRun(plan, command, null);
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

    public String runProjectScopeId(UUID id) {
        return repository.runProjectScopeId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行运行不存在"));
    }

    private ExecutionRunDetailResponse createManualRun(
            ExecutionPlan plan,
            TriggerExecutionRunCommand command,
            String requestKey
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
        ExecutionRun run = new ExecutionRun(
                runId,
                plan.id(),
                plan.projectId(),
                "QUEUED",
                "MANUAL",
                requestKey,
                null,
                1,
                traceId,
                json(Map.ofEntries(
                        Map.entry("nodeCount", orderedPlanNodes.size()),
                        Map.entry("queuedNodeCount", queuedNodeCount),
                        Map.entry("pendingNodeCount", orderedPlanNodes.size() - queuedNodeCount),
                        Map.entry("dagDigest", validation.dagDigest()),
                        Map.entry("manualReasonPresent", StringUtils.hasText(triggerReason)),
                        Map.entry("variablesAccepted", command != null && command.variables() != null
                                && !command.variables().isEmpty()),
                        Map.entry("schedulerClaimCreated", false),
                        Map.entry("runnerDispatched", false)
                )),
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

    private String mergedSummary(String existingJson, Map<String, Object> overrides) {
        Map<String, Object> summary = new LinkedHashMap<>(readMap(existingJson));
        summary.putAll(overrides);
        return json(summary);
    }
}
