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
import java.util.List;
import java.util.Map;
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
}
