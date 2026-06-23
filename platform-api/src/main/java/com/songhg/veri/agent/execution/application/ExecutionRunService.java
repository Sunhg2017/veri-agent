package com.songhg.veri.agent.execution.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.ApiAutomationService;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunDetailResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.common.transaction.OptionalTransactionTemplates;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.execution.application.command.CompleteExecutionNodeRunCommand;
import com.songhg.veri.agent.execution.application.command.DispatchExecutionNodeRunCommand;
import com.songhg.veri.agent.execution.application.command.HeartbeatExecutionQueueClaimCommand;
import com.songhg.veri.agent.execution.application.command.TriggerExecutionRunCommand;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.query.ExecutionRunLogPageRequest;
import com.songhg.veri.agent.execution.application.query.ExecutionRunPageRequest;
import com.songhg.veri.agent.execution.application.query.ExecutionRunQuery;
import com.songhg.veri.agent.execution.application.view.ExecutionQueueClaimResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionQueueRecoveryResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunExportResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunLogEntryResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunSummaryResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.notification.application.AsyncTaskNotificationService;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.uie2e.application.UiE2eRunService;
import com.songhg.veri.agent.uie2e.application.command.CancelUiE2eRunCommand;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunDetailResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
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
    private final ExecutionRepository repository;
    private final ExecutionDagValidator dagValidator;
    private final ExecutionPlatformContextClient contextClient;
    private final ExecutionActorResolver actorResolver;
    private final ApiAutomationService apiAutomationService;
    private final UiE2eRunService uiE2eRunService;
    private final ManagementStore managementStore;
    private final ExecutionRunJsonSupport jsonSupport;
    private final ExecutionRunResponseMapper responseMapper;
    private final ExecutionRunArtifactSupport artifactSupport;
    private final ExecutionAccountLeaseSupport accountLeaseSupport;
    private final ExecutionRunQueueSupport queueSupport;
    private final ExecutionRunDispatchSupport dispatchSupport;
    private final ExecutionProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final AsyncTaskNotificationService notificationService;
    private final ExecutionRunStreamService runStreamService;
    private final ExecutionRunLogHistoryService runLogHistoryService;

    public ExecutionRunService(
            ExecutionRepository repository,
            ExecutionDagValidator dagValidator,
            ExecutionPlatformContextClient contextClient,
            ExecutionActorResolver actorResolver,
            ApiAutomationService apiAutomationService,
            ObjectProvider<ManagementStore> managementStores,
            ObjectProvider<TestDataCrossWpReferenceService> testDataServices,
            ObjectProvider<UiE2eRunService> uiE2eRunServices,
            ObjectMapper objectMapper,
            ExecutionProperties properties,
            AsyncTaskNotificationService notificationService,
            ObjectProvider<PlatformTransactionManager> transactionManagers,
            ObjectProvider<ExecutionRunStreamService> runStreamServices
    ) {
        this.repository = repository;
        this.dagValidator = dagValidator;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.apiAutomationService = apiAutomationService;
        this.uiE2eRunService = uiE2eRunServices.getIfAvailable();
        this.managementStore = managementStores.getIfAvailable();
        this.jsonSupport = new ExecutionRunJsonSupport(objectMapper);
        this.responseMapper = new ExecutionRunResponseMapper(objectMapper);
        this.runLogHistoryService = new ExecutionRunLogHistoryService(repository, jsonSupport);
        this.artifactSupport = new ExecutionRunArtifactSupport(this.uiE2eRunService);
        this.properties = properties;
        this.notificationService = notificationService;
        this.runStreamService = runStreamServices.getIfAvailable();
        this.accountLeaseSupport = new ExecutionAccountLeaseSupport(
                repository,
                testDataServices.getIfAvailable(),
                properties,
                jsonSupport
        );
        this.queueSupport = new ExecutionRunQueueSupport(
                repository,
                contextClient,
                properties,
                jsonSupport,
                responseMapper,
                accountLeaseSupport,
                notificationService,
                runStreamService
        );
        this.transactionTemplate = OptionalTransactionTemplates.create(transactionManagers);
        this.dispatchSupport = new ExecutionRunDispatchSupport(
                repository,
                apiAutomationService,
                uiE2eRunService,
                managementStore,
                properties,
                jsonSupport,
                accountLeaseSupport,
                queueSupport,
                responseMapper,
                this::inExecutionTransaction,
                runStreamService
        );
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
        String requestKey = SensitiveTextSanitizer.boundedNullableText(command == null ? null : command.requestKey(), 128);
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
        String normalizedTriggerType = SensitiveTextSanitizer.boundedNullableText(triggerType, 32);
        if (!Set.of("WEBHOOK", "CRON").contains(normalizedTriggerType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_TRIGGER_TYPE_INVALID");
        }
        String normalizedRequestKey = SensitiveTextSanitizer.boundedNullableText(requestKey, 128);
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
                        SensitiveTextSanitizer.boundedNullableText(sourceEventId, 256),
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
                .map(run -> responseMapper.toSummary(run, nodeCounts.getOrDefault(run.id(), 0)))
                .toList();
        return PageResponse.of(items, request.getIndex(), request.getSize(), repository.countRuns(query));
    }

    @Transactional(readOnly = true)
    public ExecutionRunDetailResponse run(UUID id) {
        return detail(requireRun(id), false);
    }

    @Transactional(readOnly = true)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamRun(UUID id) {
        return runStreamService == null
                ? new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(0L)
                : runStreamService.subscribe(id, detail(requireRun(id), false));
    }

    @Transactional(readOnly = true)
    public PageResponse<ExecutionRunLogEntryResponse> runLogs(UUID id, ExecutionRunLogPageRequest request) {
        requireRun(id);
        return runLogHistoryService.history(id, request.toQuery());
    }

    /**
     * Exports the orchestration-safe run evidence without runner payloads or runtime secrets.
     *
     * <p>The export intentionally reuses the normal run detail response because that view is already constrained to
     * sanitized summaries, node states and digest evidence. Raw stdout/stderr, request/response bodies, base URLs,
     * secret references and claim tokens remain outside the export contract.</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ExecutionRunExportResponse exportRun(UUID id) {
        ExecutionRun run = requireRun(id);
        ExecutionRunDetailResponse detail = detail(run, false);
        Map<String, Integer> nodeStatusCounts = responseMapper.nodeStatusCounts(detail);
        ExecutionRunExportResponse response = new ExecutionRunExportResponse(
                "wp9-run-export-v1",
                Instant.now(),
                detail,
                nodeStatusCounts,
                responseMapper.runExportRedactionPolicy()
        );
        auditRun(run, "execution.run.exported", "SUCCESS", Map.of(
                "nodeCount", detail.nodes().size(),
                "artifactManifestCount", detail.artifacts().size(),
                "nodeStatusCounts", nodeStatusCounts,
                "rawOutputExported", false,
                "rawRequestResponseExported", false,
                "rawArtifactDownloadExported", false,
                "secretRefsExported", false,
                "claimTokenExported", false
        ));
        return response;
    }

    /**
     * Cancels active WP9 runs and forwards best-effort cancellation to already-dispatched WP6 node runs.
     *
     * <p>WP9 keeps cancellation idempotent by closing its control-plane records regardless of runner response. Only the
     * external WP6 run ID is sent to WP6, and the persisted summary records bounded attempt counts instead of raw runner
     * output.</p>
     */
    public ExecutionRunDetailResponse cancelRun(UUID id) {
        ExecutionRun run = inExecutionTransaction(() -> requireRun(id));
        if (TERMINAL_RUN_STATUSES.contains(run.status())) {
            return inExecutionTransaction(() -> detail(run, false));
        }
        List<ExecutionNodeRun> existingNodeRuns = inExecutionTransaction(() -> repository.nodeRuns(run.id()));
        RunnerCancelSummary runnerCancelSummary = cancelDispatchedRunnerNodes(existingNodeRuns);
        return inExecutionTransaction(() -> persistCanceledRun(id, runnerCancelSummary));
    }

    private ExecutionRunDetailResponse persistCanceledRun(UUID id, RunnerCancelSummary runnerCancelSummary) {
        ExecutionRun run = requireRun(id);
        if (TERMINAL_RUN_STATUSES.contains(run.status())) {
            return detail(run, false);
        }
        Instant now = Instant.now();
        List<ExecutionNodeRun> existingNodeRuns = repository.nodeRuns(run.id());
        List<ExecutionNodeRun> canceledNodeRuns = existingNodeRuns.stream()
                .filter(nodeRun -> CANCELABLE_NODE_STATUSES.contains(nodeRun.status()))
                .map(nodeRun -> canceledNodeRun(nodeRun, now, runnerCancelSummary.forNode(nodeRun)))
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
                jsonSupport.mergedSummary(run.resultSummaryJson(), runCancelSummary(canceledNodeRuns.size(), runnerCancelSummary)),
                "EXECUTION_RUN_CANCELED",
                runnerCancelSummary.attempted()
                        ? "Execution run canceled with best-effort runner cancellation"
                        : "Execution run canceled before runner dispatch",
                run.createdBy(),
                run.startedAt(),
                now,
                run.createdAt(),
                now
        );
        repository.updateRun(canceled);
        repository.updateNodeRuns(canceledNodeRuns);
        ExecutionRun releaseAware = accountLeaseSupport.releaseTerminalRunLeases(
                canceled,
                repository.nodeRuns(canceled.id()),
                now
        );
        auditRun(releaseAware, "execution.run.canceled", "SUCCESS", Map.of(
                "status", releaseAware.status(),
                "canceledNodeCount", canceledNodeRuns.size(),
                "runnerCancelAttempted", runnerCancelSummary.attempted(),
                "runnerCancelAttemptCount", runnerCancelSummary.attemptedCount(),
                "runnerCancelAcceptedCount", runnerCancelSummary.acceptedCount(),
                "runnerCancelFailedCount", runnerCancelSummary.failedCount()
        ));
        notificationService.notifyExecutionRunFinished(releaseAware);
        releaseActiveClaims(existingNodeRuns, now);
        ExecutionRunDetailResponse detail = detail(requireRun(releaseAware.id()), false);
        publishRunEvent(
                detail,
                runnerCancelSummary.failedCount() > 0 ? "WARN" : "SUCCESS",
                "run.canceled",
                runnerCancelSummary.attempted()
                        ? "Execution run canceled with downstream runner callback"
                        : "Execution run canceled before runner dispatch",
                null,
                Map.of(
                        "canceledNodeCount", canceledNodeRuns.size(),
                        "runnerCancelAttemptCount", runnerCancelSummary.attemptedCount(),
                        "runnerCancelAcceptedCount", runnerCancelSummary.acceptedCount(),
                        "runnerCancelFailedCount", runnerCancelSummary.failedCount()
                )
        );
        return detail;
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
        List<ExecutionNodeRun> retryableNodeRuns = queueSupport.latestNodeRuns(existingNodeRuns).stream()
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
                jsonSupport.mergedSummary(run.resultSummaryJson(), Map.of(
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
        ExecutionRunDetailResponse detail = detail(requireRun(id), false);
        publishRunEvent(detail, "INFO", "run.retried", "Execution run retry submitted", null, Map.of(
                "attempt", detail.attempt(),
                "retryNodeCount", retryNodeRuns.size()
        ));
        return detail;
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
        return queueSupport.claimNextQueuedNode(workerId);
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
        return queueSupport.completeClaimedNodeRun(command);
    }

    /**
     * Dispatches a claimed API_TEST node through the WP6 application service and closes the WP9 node with the
     * sanitized WP6 run outcome.
     *
     * <p>The runtime baseUrl and secretRefs are passed only to WP6. WP9 stores the WP6 run ID, host/digest evidence and
     * aggregate counts, keeping raw target URLs, secret references and runner payloads out of execution summaries.</p>
     */
    public ExecutionRunDetailResponse dispatchClaimedApiTestNodeRun(DispatchExecutionNodeRunCommand command) {
        return dispatchSupport.dispatchClaimedApiTestNodeRun(command);
    }

    /**
     * Routes internal claimed-node dispatch by runner type so the controller can stay thin while WP9 keeps a single
     * dispatch entrypoint for scheduler and manual admin flows.
     */
    public ExecutionRunDetailResponse dispatchClaimedNodeRun(DispatchExecutionNodeRunCommand command) {
        if (command != null && command.nodeRunId() != null) {
            Optional<ExecutionNodeRun> nodeRun = repository.nodeRun(command.nodeRunId());
            if (nodeRun.isPresent() && "WP7_UI".equals(nodeRun.get().runnerType())) {
                return dispatchClaimedUiTestNodeRun(command);
            }
        }
        return dispatchClaimedApiTestNodeRun(command);
    }

    public ExecutionRunDetailResponse dispatchClaimedUiTestNodeRun(DispatchExecutionNodeRunCommand command) {
        if (command != null && command.nodeRunId() != null) {
            Optional<ExecutionNodeRun> nodeRun = repository.nodeRun(command.nodeRunId());
            if (nodeRun.isPresent()
                    && "WP7_UI".equals(nodeRun.get().runnerType())
                    && StringUtils.hasText(nodeRun.get().externalRunId())
                    && Boolean.TRUE.equals(jsonSupport.readMap(nodeRun.get().resultSummaryJson()).get("wp7AsyncFollowUpRequired"))) {
                return dispatchSupport.followUpClaimedUiTestNodeRun(command);
            }
        }
        return dispatchSupport.dispatchClaimedUiTestNodeRun(command);
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
        return queueSupport.heartbeatQueueClaim(command);
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
        return queueSupport.recoverExpiredQueueClaims();
    }

    public String runProjectScopeId(UUID id) {
        return repository.runProjectScopeId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行运行不存在"));
    }

    /**
     * Downloads one runner artifact through the WP9 execution surface while keeping provider-specific storage opaque.
     */
    @Transactional(readOnly = true)
    public DownloadableArtifact downloadArtifact(UUID runId, UUID artifactId) {
        ExecutionRun run = requireRun(runId);
        List<ExecutionNodeRun> nodeRuns = repository.nodeRuns(run.id());
        List<ExecutionPlanNode> planNodes = repository.planNodes(run.planId());
        ExecutionRunArtifactSupport.DownloadableArtifact artifact = artifactSupport.downloadArtifact(
                artifactId,
                nodeRuns,
                planNodes
        );
        auditRun(run, "execution.run.artifact.downloaded", "SUCCESS", Map.of(
                "nodeRunId", String.valueOf(artifact.nodeRunId()),
                "artifactId", String.valueOf(artifact.artifactId()),
                "runnerType", artifact.runnerType(),
                "artifactType", artifact.artifactType(),
                "rawArtifactBodyExported", true
        ));
        return new DownloadableArtifact(
                artifact.fileName(),
                normalizeContentType(artifact.contentType()),
                artifact.content()
        );
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
        String triggerReason = SensitiveTextSanitizer.boundedNullableText(command == null ? null : command.reason(), 256);
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
                jsonSupport.json(summary),
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
        ExecutionRunDetailResponse detail = detail(run, false, nodeRuns, orderedPlanNodes);
        publishRunEvent(detail, "INFO", "run.created", "Execution run created", null, Map.of(
                "triggerType", detail.triggerType(),
                "nodeCount", detail.nodes().size(),
                "requestKeyPresent", StringUtils.hasText(detail.requestKey())
        ));
        return detail;
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
                jsonSupport.json(Map.of(
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

    private Map<String, Object> runCancelSummary(int canceledNodeCount, RunnerCancelSummary runnerCancelSummary) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("canceled", true);
        summary.put("canceledNodeCount", canceledNodeCount);
        summary.put("runnerCancelAttempted", runnerCancelSummary.attempted());
        summary.put("runnerCancelAttemptCount", runnerCancelSummary.attemptedCount());
        summary.put("runnerCancelAcceptedCount", runnerCancelSummary.acceptedCount());
        summary.put("runnerCancelFailedCount", runnerCancelSummary.failedCount());
        summary.put("runnerDispatched", false);
        return summary;
    }

    private ExecutionNodeRun canceledNodeRun(ExecutionNodeRun nodeRun, Instant now, RunnerCancelAttempt runnerCancel) {
        boolean runnerCancelAttempted = runnerCancel != null && runnerCancel.attempted();
        return new ExecutionNodeRun(
                nodeRun.id(),
                nodeRun.runId(),
                nodeRun.planNodeId(),
                "CANCELED",
                nodeRun.attempt(),
                nodeRun.runnerType(),
                nodeRun.externalRunId(),
                "EXECUTION_RUN_CANCELED",
                runnerCancelAttempted
                        ? "Execution node canceled with best-effort runner cancellation"
                        : "Execution node canceled before runner dispatch",
                jsonSupport.mergedSummary(nodeRun.resultSummaryJson(), nodeCancelSummary(runnerCancel)),
                nodeRun.heartbeatAt(),
                nodeRun.queuedAt(),
                nodeRun.startedAt(),
                now,
                nodeRun.createdAt(),
                now
        );
    }

    private Map<String, Object> nodeCancelSummary(RunnerCancelAttempt runnerCancel) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("canceled", true);
        summary.put("runnerCancelAttempted", runnerCancel != null && runnerCancel.attempted());
        summary.put("runnerCancelAccepted", runnerCancel != null && runnerCancel.accepted());
        if (runnerCancel != null && StringUtils.hasText(runnerCancel.errorCode())) {
            summary.put("runnerCancelErrorCode", runnerCancel.errorCode());
        }
        if (runnerCancel != null && StringUtils.hasText(runnerCancel.errorSummary())) {
            summary.put("runnerCancelErrorSummary", runnerCancel.errorSummary());
        }
        summary.put("runnerDispatched", false);
        return summary;
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
                jsonSupport.json(Map.ofEntries(
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

    private boolean retryAlreadyQueued(ExecutionRun run) {
        return "QUEUED".equals(run.status())
                && "RETRY".equals(run.triggerType())
                && Boolean.TRUE.equals(jsonSupport.readMap(run.resultSummaryJson()).get("retryInFlight"));
    }

    private RunnerCancelSummary cancelDispatchedRunnerNodes(List<ExecutionNodeRun> nodeRuns) {
        List<RunnerCancelAttempt> attempts = nodeRuns.stream()
                .filter(nodeRun -> CANCELABLE_NODE_STATUSES.contains(nodeRun.status()))
                .filter(nodeRun -> StringUtils.hasText(nodeRun.externalRunId()))
                .map(nodeRun -> switch (nodeRun.runnerType()) {
                    case "WP6_API" -> cancelDispatchedWp6Run(nodeRun);
                    case "WP7_UI" -> cancelDispatchedWp7Run(nodeRun);
                    default -> null;
                })
                .filter(Objects::nonNull)
                .toList();
        return new RunnerCancelSummary(attempts);
    }

    private RunnerCancelAttempt cancelDispatchedWp6Run(ExecutionNodeRun nodeRun) {
        UUID wp6RunId = uuidOrNull(nodeRun.externalRunId());
        if (wp6RunId == null) {
            return RunnerCancelAttempt.failed(
                    nodeRun.id(),
                    "EXECUTION_RUNNER_CANCEL_ID_INVALID",
                    "Dispatched WP6 run id is invalid"
            );
        }
        try {
            ApiAutomationRunDetailResponse response = apiAutomationService.cancelRun(wp6RunId);
            String status = response == null || response.run() == null ? null : response.run().status();
            boolean accepted = "CANCELED".equals(status);
            return new RunnerCancelAttempt(
                    nodeRun.id(),
                    true,
                    accepted,
                    accepted ? null : "EXECUTION_RUNNER_CANCEL_NOT_ACCEPTED",
                    accepted ? null : "WP6 runner cancel was not accepted"
            );
        } catch (BusinessException exception) {
            return RunnerCancelAttempt.failed(
                    nodeRun.id(),
                    SensitiveTextSanitizer.boundedNullableText(exception.getErrorCode().name(), 64),
                    queueSupport.terminalErrorSummary("FAILED", exception.getMessage())
            );
        } catch (RuntimeException exception) {
            return RunnerCancelAttempt.failed(
                    nodeRun.id(),
                    "EXECUTION_RUNNER_CANCEL_FAILED",
                    queueSupport.terminalErrorSummary("FAILED", exception.getMessage())
            );
        }
    }

    private RunnerCancelAttempt cancelDispatchedWp7Run(ExecutionNodeRun nodeRun) {
        UUID wp7RunId = uuidOrNull(nodeRun.externalRunId());
        if (wp7RunId == null) {
            return RunnerCancelAttempt.failed(
                    nodeRun.id(),
                    "EXECUTION_RUNNER_CANCEL_ID_INVALID",
                    "Dispatched WP7 run id is invalid"
            );
        }
        try {
            UiE2eRunDetailResponse response = uiE2eRunService.cancelRun(
                    wp7RunId,
                    new CancelUiE2eRunCommand("wp9 execution run canceled")
            );
            String status = response == null ? null : response.status();
            boolean accepted = "CANCELED".equals(status);
            return new RunnerCancelAttempt(
                    nodeRun.id(),
                    true,
                    accepted,
                    accepted ? null : "EXECUTION_RUNNER_CANCEL_NOT_ACCEPTED",
                    accepted ? null : "WP7 runner cancel was not accepted"
            );
        } catch (BusinessException exception) {
            return RunnerCancelAttempt.failed(
                    nodeRun.id(),
                    SensitiveTextSanitizer.boundedNullableText(exception.getErrorCode().name(), 64),
                    queueSupport.terminalErrorSummary("FAILED", exception.getMessage())
            );
        } catch (RuntimeException exception) {
            return RunnerCancelAttempt.failed(
                    nodeRun.id(),
                    "EXECUTION_RUNNER_CANCEL_FAILED",
                    queueSupport.terminalErrorSummary("FAILED", exception.getMessage())
            );
        }
    }

    private UUID uuidOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private <T> T inExecutionTransaction(Supplier<T> action) {
        return transactionTemplate.execute(ignored -> action.get());
    }

    private void releaseActiveClaims(List<ExecutionNodeRun> nodeRuns, Instant now) {
        nodeRuns.stream()
                .map(ExecutionNodeRun::id)
                .forEach(nodeRunId -> repository.activeQueueClaim(nodeRunId)
                        .ifPresent(claim -> repository.updateQueueClaimIfStatus(
                                new com.songhg.veri.agent.execution.domain.ExecutionQueueClaim(
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
                                ),
                                "CLAIMED"
                        )));
    }

    private void publishRunEvent(
            ExecutionRunDetailResponse run,
            String level,
            String stage,
            String message,
            UUID nodeRunId,
            Map<String, Object> metadata
    ) {
        if (runStreamService == null || run == null) {
            return;
        }
        runStreamService.publish(run, level, stage, message, nodeRunId, metadata);
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
        return responseMapper.toDetail(
                run,
                idempotentReplay,
                nodeRuns,
                planNodes,
                artifactSupport.artifacts(nodeRuns, planNodes)
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
                        jsonSupport.readMap(node.inputSummaryJson()),
                        node.timeoutSeconds(),
                        node.failurePolicy(),
                        jsonSupport.readMap(node.retryPolicyJson())
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

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType.trim() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    public record DownloadableArtifact(
            String fileName,
            String contentType,
            byte[] content
    ) {
    }

    private record RunnerCancelAttempt(
            UUID nodeRunId,
            boolean attempted,
            boolean accepted,
            String errorCode,
            String errorSummary
    ) {
        private static RunnerCancelAttempt failed(UUID nodeRunId, String errorCode, String errorSummary) {
            return new RunnerCancelAttempt(nodeRunId, true, false, errorCode, errorSummary);
        }
    }

    private record RunnerCancelSummary(List<RunnerCancelAttempt> attempts) {
        private boolean attempted() {
            return !attempts.isEmpty();
        }

        private long attemptedCount() {
            return attempts.size();
        }

        private long acceptedCount() {
            return attempts.stream().filter(RunnerCancelAttempt::accepted).count();
        }

        private long failedCount() {
            return attemptedCount() - acceptedCount();
        }

        private RunnerCancelAttempt forNode(ExecutionNodeRun nodeRun) {
            if (nodeRun == null) {
                return null;
            }
            return attempts.stream()
                    .filter(attempt -> nodeRun.id().equals(attempt.nodeRunId()))
                    .findFirst()
                    .orElse(null);
        }
    }

}
