package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.apiautomation.application.ApiAutomationService;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationRunCommand;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunResultResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.execution.application.command.CompleteExecutionNodeRunCommand;
import com.songhg.veri.agent.execution.application.command.DispatchExecutionNodeRunCommand;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionQueueClaim;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.management.application.port.ManagementStoreParams;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentRuntimeRef;
import com.songhg.veri.agent.uie2e.application.UiE2eRunService;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eRunCommand;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunDetailResponse;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.util.StringUtils;

/**
 * Owns WP9 claimed runner dispatch into downstream WP6/WP7 services while keeping runtime secrets and base URLs out
 * of WP9 storage.
 */
final class ExecutionRunDispatchSupport {

    private static final Set<String> COMPLETABLE_NODE_STATUSES = Set.of(
            "SUCCEEDED", "SKIPPED", "FAILED", "TIMEOUT", "BLOCKED"
    );
    private static final Set<String> SUCCESS_NODE_STATUSES = Set.of("SUCCEEDED", "SKIPPED");

    private final ExecutionRepository repository;
    private final ApiAutomationService apiAutomationService;
    private final UiE2eRunService uiE2eRunService;
    private final ManagementStore managementStore;
    private final ExecutionProperties properties;
    private final ExecutionRunJsonSupport jsonSupport;
    private final ExecutionAccountLeaseSupport accountLeaseSupport;
    private final ExecutionRunQueueSupport queueSupport;
    private final ExecutionRunResponseMapper responseMapper;
    private final TransactionBridge transactionBridge;
    private final ExecutionRunDispatchEventSupport eventSupport;

    ExecutionRunDispatchSupport(
            ExecutionRepository repository,
            ApiAutomationService apiAutomationService,
            UiE2eRunService uiE2eRunService,
            ManagementStore managementStore,
            ExecutionProperties properties,
            ExecutionRunJsonSupport jsonSupport,
            ExecutionAccountLeaseSupport accountLeaseSupport,
            ExecutionRunQueueSupport queueSupport,
            ExecutionRunResponseMapper responseMapper,
            TransactionBridge transactionBridge,
            ExecutionRunEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.apiAutomationService = apiAutomationService;
        this.uiE2eRunService = uiE2eRunService;
        this.managementStore = managementStore;
        this.properties = properties;
        this.jsonSupport = jsonSupport;
        this.accountLeaseSupport = accountLeaseSupport;
        this.queueSupport = queueSupport;
        this.responseMapper = responseMapper;
        this.transactionBridge = transactionBridge;
        this.eventSupport = new ExecutionRunDispatchEventSupport(repository, responseMapper, eventPublisher);
    }
    ExecutionRunDetailResponse dispatchClaimedApiTestNodeRun(DispatchExecutionNodeRunCommand command) {
        if (command == null || command.nodeRunId() == null || !StringUtils.hasText(command.claimToken())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_QUEUE_CLAIM_REQUIRED");
        }
        ApiTestDispatchPreparation preparation = transactionBridge.inExecutionTransaction(
                () -> prepareApiTestDispatch(command)
        );
        if (preparation.replayResponse() != null) {
            return preparation.replayResponse();
        }
        ApiAutomationRunDetailResponse wp6Run;
        try {
            wp6Run = apiAutomationService.createRun(new CreateApiAutomationRunCommand(
                    preparation.bundleId(),
                    preparation.environmentId(),
                    preparation.baseUrl(),
                    preparation.caseIds(),
                    preparation.timeoutSeconds(),
                    preparation.secretRefs()
            ));
        } catch (RuntimeException exception) {
            transactionBridge.inExecutionTransaction(() -> failPreparedDispatch(preparation, exception));
            throw exception;
        }
        eventSupport.publishWp6DispatchStarted(preparation);
        return transactionBridge.inExecutionTransaction(
                () -> completeDispatchedApiTestNodeRun(preparation, wp6Run)
        );
    }
    ExecutionRunDetailResponse dispatchClaimedUiTestNodeRun(DispatchExecutionNodeRunCommand command) {
        if (command == null || command.nodeRunId() == null || !StringUtils.hasText(command.claimToken())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_QUEUE_CLAIM_REQUIRED");
        }
        UiTestDispatchPreparation preparation = transactionBridge.inExecutionTransaction(
                () -> prepareUiTestDispatch(command)
        );
        if (preparation.replayResponse() != null) {
            return preparation.replayResponse();
        }
        UiE2eRunDetailResponse wp7Run;
        try {
            wp7Run = uiE2eRunService.createRun(new CreateUiE2eRunCommand(
                    preparation.projectId(),
                    preparation.sceneId(),
                    preparation.bundleId(),
                    preparation.environmentId(),
                    preparation.baseUrlRef(),
                    preparation.accountLeaseRef(),
                    preparation.requestKey(),
                    preparation.reason()
            ));
        } catch (RuntimeException exception) {
            transactionBridge.inExecutionTransaction(() -> failPreparedUiTestDispatch(preparation, exception));
            throw exception;
        }
        eventSupport.publishWp7DispatchStarted(preparation);
        return transactionBridge.inExecutionTransaction(
                () -> completeDispatchedUiTestNodeRun(preparation, wp7Run)
        );
    }
    ExecutionRunDetailResponse followUpClaimedUiTestNodeRun(DispatchExecutionNodeRunCommand command) {
        if (command == null || command.nodeRunId() == null || !StringUtils.hasText(command.claimToken())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_QUEUE_CLAIM_REQUIRED");
        }
        UiTestFollowUpPreparation preparation = transactionBridge.inExecutionTransaction(
                () -> prepareUiTestFollowUp(command)
        );
        if (preparation.replayResponse() != null) {
            return preparation.replayResponse();
        }
        UiE2eRunDetailResponse wp7Run = uiE2eRunService.run(preparation.wp7RunId());
        eventSupport.publishWp7FollowUpPoll(preparation);
        return transactionBridge.inExecutionTransaction(
                () -> completeUiTestFollowUp(preparation, wp7Run)
        );
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
        Map<String, Object> planInput = jsonSupport.readMap(planNode.inputSummaryJson());
        ResolvedDispatchTarget target = resolvedDispatchTarget(command, planInput, run.projectId());
        List<String> secretRefs = dispatchSecretRefs(command, planInput);
        renewClaimForDispatch(claim, planNode, now);
        accountLeaseSupport.acquireForDispatch(run, planNode, nodeRun, planInput);
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

    private UiTestDispatchPreparation prepareUiTestDispatch(DispatchExecutionNodeRunCommand command) {
        if (uiE2eRunService == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_WP7_DISPATCH_UNAVAILABLE");
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
            return UiTestDispatchPreparation.replay(detail(requireRun(nodeRun.runId()), false));
        }
        if (!"CLAIMED".equals(claim.status()) || !claim.expiresAt().isAfter(now) || !"RUNNING".equals(nodeRun.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_NOT_ACTIVE");
        }

        ExecutionRun run = requireRun(nodeRun.runId());
        ExecutionPlanNode planNode = repository.planNodes(run.planId()).stream()
                .filter(node -> node.id().equals(nodeRun.planNodeId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行计划节点不存在"));
        if (!"UI_TEST".equals(planNode.nodeType()) || !"WP7_UI".equals(nodeRun.runnerType())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_NODE_DISPATCH_UNSUPPORTED");
        }
        Map<String, Object> planInput = jsonSupport.readMap(planNode.inputSummaryJson());
        renewClaimForDispatch(claim, planNode, now);
        ExecutionNodeRun leaseAwareNodeRun = accountLeaseSupport.acquireForDispatch(run, planNode, nodeRun, planInput);
        UUID accountLeaseRef = acquiredAccountLeaseRef(leaseAwareNodeRun);
        return new UiTestDispatchPreparation(
                leaseAwareNodeRun.id(),
                command.claimToken(),
                run.projectId(),
                uiTestSceneId(planInput),
                uiTestBundleId(planInput),
                dispatchUiTestEnvironmentId(command, planInput),
                uiTestBaseUrlRef(command, planInput),
                accountLeaseRef,
                uiTestRequestKey(run, planNode, leaseAwareNodeRun),
                uiTestReason(planNode),
                null
        );
    }
    private UiTestFollowUpPreparation prepareUiTestFollowUp(DispatchExecutionNodeRunCommand command) {
        if (uiE2eRunService == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_WP7_DISPATCH_UNAVAILABLE");
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
            return UiTestFollowUpPreparation.replay(detail(requireRun(nodeRun.runId()), false));
        }
        if (!"CLAIMED".equals(claim.status()) || !claim.expiresAt().isAfter(now) || !"RUNNING".equals(nodeRun.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_NOT_ACTIVE");
        }
        if (!"WP7_UI".equals(nodeRun.runnerType()) || !StringUtils.hasText(nodeRun.externalRunId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_NODE_DISPATCH_UNSUPPORTED");
        }
        UUID wp7RunId = uuidOrNull(nodeRun.externalRunId());
        if (wp7RunId == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_WP7_RUN_ID_INVALID");
        }
        ExecutionRun run = requireRun(nodeRun.runId());
        Map<String, Object> summary = jsonSupport.readMap(nodeRun.resultSummaryJson());
        if (!Boolean.TRUE.equals(summary.get("wp7AsyncFollowUpRequired"))) {
            return UiTestFollowUpPreparation.replay(detail(run, false));
        }
        return new UiTestFollowUpPreparation(nodeRun.id(), command.claimToken(), wp7RunId, null);
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
                queueSupport.terminalErrorCode(targetStatus, wp6DispatchErrorCode(targetStatus, wp6Run.run())),
                queueSupport.terminalErrorSummary(targetStatus, wp6Run.run().errorSummary()),
                jsonSupport.mergedSummary(nodeRun.resultSummaryJson(), wp6DispatchSummary(wp6Run, preparation, completedAt)),
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
        ExecutionRunDetailResponse detail = queueSupport.aggregateRunAfterNodeCompletion(completed.runId(), completedAt);
        eventSupport.publishWp6DispatchCompleted(completed, targetStatus, wp6Run);
        return detail;
    }

    private ExecutionRunDetailResponse completeDispatchedUiTestNodeRun(
            UiTestDispatchPreparation preparation,
            UiE2eRunDetailResponse wp7Run
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
        if (!wp7TerminalStatus(wp7Run.status())) {
            ExecutionNodeRun waiting = uiTestFollowUpNodeRun(nodeRun, preparation, wp7Run, completedAt);
            repository.updateNodeRuns(List.of(waiting));
            repository.updateRun(markRunFollowUpPending(requireRun(waiting.runId()), completedAt, wp7Run.status()));
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
            ExecutionRunDetailResponse detail = detail(requireRun(waiting.runId()), false);
            eventSupport.publishWp7FollowUpRequired(waiting, wp7Run);
            return detail;
        }
        String targetStatus = wp9StatusFromWp7(wp7Run.status());
        ExecutionNodeRun completed = new ExecutionNodeRun(
                nodeRun.id(),
                nodeRun.runId(),
                nodeRun.planNodeId(),
                targetStatus,
                nodeRun.attempt(),
                nodeRun.runnerType(),
                wp7Run.id().toString(),
                queueSupport.terminalErrorCode(targetStatus, wp7DispatchErrorCode(targetStatus, wp7Run)),
                queueSupport.terminalErrorSummary(targetStatus, wp7Run.failureSummary()),
                jsonSupport.mergedSummary(nodeRun.resultSummaryJson(), wp7DispatchSummary(wp7Run, preparation, completedAt)),
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
        ExecutionRunDetailResponse detail = queueSupport.aggregateRunAfterNodeCompletion(completed.runId(), completedAt);
        eventSupport.publishWp7DispatchCompleted(completed, targetStatus, wp7Run);
        return detail;
    }

    private ExecutionRunDetailResponse completeUiTestFollowUp(
            UiTestFollowUpPreparation preparation,
            UiE2eRunDetailResponse wp7Run
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
            ExecutionRunDetailResponse detail = detail(requireRun(nodeRun.runId()), false);
            eventSupport.publishWp7FollowUpStillActive(nodeRun, wp7Run);
            return detail;
        }
        if (!"CLAIMED".equals(claim.status())
                || !claim.expiresAt().isAfter(completedAt)
                || !"RUNNING".equals(nodeRun.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_QUEUE_CLAIM_NOT_ACTIVE");
        }
        if (!wp7TerminalStatus(wp7Run.status())) {
            ExecutionNodeRun heartbeat = new ExecutionNodeRun(
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
                            "lastFollowUpAt", completedAt.toString(),
                            "wp7Status", wp7Run.status(),
                            "wp7TerminalSnapshot", false,
                            "wp7AsyncFollowUpRequired", true,
                            "runnerDispatched", true
                    )),
                    completedAt,
                    nodeRun.queuedAt(),
                    nodeRun.startedAt(),
                    nodeRun.finishedAt(),
                    nodeRun.createdAt(),
                    completedAt
            );
            repository.updateNodeRuns(List.of(heartbeat));
            repository.updateRun(markRunFollowUpPending(requireRun(nodeRun.runId()), completedAt, wp7Run.status()));
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
            return detail(requireRun(nodeRun.runId()), false);
        }
        String targetStatus = wp9StatusFromWp7(wp7Run.status());
        ExecutionNodeRun completed = new ExecutionNodeRun(
                nodeRun.id(),
                nodeRun.runId(),
                nodeRun.planNodeId(),
                targetStatus,
                nodeRun.attempt(),
                nodeRun.runnerType(),
                wp7Run.id().toString(),
                queueSupport.terminalErrorCode(targetStatus, wp7DispatchErrorCode(targetStatus, wp7Run)),
                queueSupport.terminalErrorSummary(targetStatus, wp7Run.failureSummary()),
                jsonSupport.mergedSummary(nodeRun.resultSummaryJson(), wp7DispatchSummary(wp7Run, completedAt)),
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
        ExecutionRunDetailResponse detail = queueSupport.aggregateRunAfterNodeCompletion(completed.runId(), completedAt);
        eventSupport.publishWp7FollowUpCompleted(completed, targetStatus, wp7Run);
        return detail;
    }

    private ExecutionRunDetailResponse failPreparedDispatch(ApiTestDispatchPreparation preparation, RuntimeException exception) {
        eventSupport.publishWp6DispatchFailed(preparation, sourceErrorCode(exception));
        return queueSupport.completeClaimedNodeRun(new CompleteExecutionNodeRunCommand(
                preparation.nodeRunId(),
                preparation.claimToken(),
                "FAILED",
                "EXECUTION_NODE_DISPATCH_FAILED",
                SensitiveTextSanitizer.sanitizedErrorSummary(
                        exception == null ? null : exception.getMessage(),
                        "WP6 dispatch failed",
                        512
                ),
                Map.of(
                        "wp6DispatchFailed", true,
                        "runnerDispatched", false,
                        "sourceErrorCode", sourceErrorCode(exception)
                )
        ));
    }

    private ExecutionRunDetailResponse failPreparedUiTestDispatch(
            UiTestDispatchPreparation preparation,
            RuntimeException exception
    ) {
        eventSupport.publishWp7DispatchFailed(preparation, sourceErrorCode(exception));
        return queueSupport.completeClaimedNodeRun(new CompleteExecutionNodeRunCommand(
                preparation.nodeRunId(),
                preparation.claimToken(),
                "FAILED",
                "EXECUTION_NODE_DISPATCH_FAILED",
                SensitiveTextSanitizer.sanitizedErrorSummary(
                        exception == null ? null : exception.getMessage(),
                        "WP7 dispatch failed",
                        512
                ),
                Map.of(
                        "wp7DispatchFailed", true,
                        "runnerDispatched", false,
                        "sourceErrorCode", sourceErrorCode(exception)
                )
        ));
    }

    private ExecutionNodeRun uiTestFollowUpNodeRun(
            ExecutionNodeRun nodeRun,
            UiTestDispatchPreparation preparation,
            UiE2eRunDetailResponse wp7Run,
            Instant now
    ) {
        return new ExecutionNodeRun(
                nodeRun.id(),
                nodeRun.runId(),
                nodeRun.planNodeId(),
                "RUNNING",
                nodeRun.attempt(),
                nodeRun.runnerType(),
                wp7Run.id().toString(),
                null,
                null,
                jsonSupport.mergedSummary(nodeRun.resultSummaryJson(), wp7DispatchSummary(wp7Run, preparation, now)),
                now,
                nodeRun.queuedAt(),
                nodeRun.startedAt(),
                null,
                nodeRun.createdAt(),
                now
        );
    }

    private ExecutionRun markRunFollowUpPending(ExecutionRun run, Instant now, String wp7Status) {
        return new ExecutionRun(
                run.id(),
                run.planId(),
                run.projectId(),
                run.status(),
                run.triggerType(),
                run.requestKey(),
                run.sourceEventId(),
                run.attempt(),
                run.traceId(),
                jsonSupport.mergedSummary(run.resultSummaryJson(), Map.of(
                        "runnerDispatched", true,
                        "wp7AsyncFollowUpPending", true,
                        "wp7LastObservedStatus", wp7Status,
                        "wp7LastObservedAt", now.toString()
                )),
                run.errorCode(),
                run.errorSummary(),
                run.createdBy(),
                run.startedAt(),
                run.finishedAt(),
                run.createdAt(),
                now
        );
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
        Object bundleId = jsonSupport.readMap(planNode.inputSummaryJson()).get("apiAutomationBundleId");
        try {
            return UUID.fromString(String.valueOf(bundleId));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_RESOURCE_REQUIRED");
        }
    }

    private UUID uiTestSceneId(Map<String, Object> planInput) {
        UUID sceneId = uuidOrNull(valueText(planInput.get("sceneId")));
        if (sceneId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_UI_TEST_SCENE_REQUIRED");
        }
        return sceneId;
    }

    private UUID uiTestBundleId(Map<String, Object> planInput) {
        UUID bundleId = uuidOrNull(valueText(planInput.get("bundleId")));
        if (bundleId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_UI_TEST_BUNDLE_REQUIRED");
        }
        return bundleId;
    }

    private String uiTestBaseUrlRef(DispatchExecutionNodeRunCommand command, Map<String, Object> planInput) {
        String baseUrlRef = firstText(command.baseUrlRef(), planInput.get("baseUrlRef"));
        if (!StringUtils.hasText(baseUrlRef)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_UI_TEST_BASE_URL_REF_REQUIRED");
        }
        String normalized = SensitiveTextSanitizer.boundedNullableText(baseUrlRef, 128);
        if (!normalized.startsWith("env:") || !StringUtils.hasText(normalized.substring("env:".length()).trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_UI_TEST_BASE_URL_REF_INVALID");
        }
        return normalized;
    }

    private String dispatchUiTestEnvironmentId(DispatchExecutionNodeRunCommand command, Map<String, Object> planInput) {
        return SensitiveTextSanitizer.boundedNullableText(
                firstText(command.environmentId(), planInput.get("environmentId")),
                128
        );
    }

    /**
     * Reuses the queue-attempt identity so repeated dispatch of the same claimed node replays the same WP7 run rather
     * than creating duplicate preview snapshots.
     */
    private String uiTestRequestKey(ExecutionRun run, ExecutionPlanNode planNode, ExecutionNodeRun nodeRun) {
        return SensitiveTextSanitizer.boundedText(
                "wp9-ui:" + run.id() + ":" + planNode.id() + ":" + nodeRun.attempt(),
                128
        );
    }

    private String uiTestReason(ExecutionPlanNode planNode) {
        return SensitiveTextSanitizer.boundedText(
                "wp9 execution node " + planNode.nodeKey(),
                512
        );
    }

    private UUID acquiredAccountLeaseRef(ExecutionNodeRun nodeRun) {
        UUID accountLeaseRef = uuidOrNull(valueText(jsonSupport.readMap(nodeRun.resultSummaryJson()).get("accountLeaseRef")));
        if (accountLeaseRef == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_ACCOUNT_LEASE_INVALID");
        }
        return accountLeaseRef;
    }

    private List<UUID> dispatchCaseIds(DispatchExecutionNodeRunCommand command, ExecutionPlanNode planNode) {
        List<UUID> commandCaseIds = normalizedUuidList(command.caseIds());
        if (!commandCaseIds.isEmpty()) {
            return commandCaseIds;
        }
        Object planCaseIds = jsonSupport.readMap(planNode.inputSummaryJson()).get("caseIds");
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
            String bounded = SensitiveTextSanitizer.boundedNullableText(secretRef, 256);
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
        String runtimeBaseUrl = SensitiveTextSanitizer.boundedNullableText(command.baseUrl(), 512);
        if (StringUtils.hasText(runtimeBaseUrl)) {
            return new ResolvedDispatchTarget(normalizedRuntimeBaseUrl(runtimeBaseUrl), "REQUEST_BASE_URL", null, null);
        }
        boolean requestBaseUrlRefProvided = StringUtils.hasText(command.baseUrlRef());
        String baseUrlRef = firstText(command.baseUrlRef(), planInput.get("baseUrlRef"));
        if (!StringUtils.hasText(baseUrlRef)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_DISPATCH_BASE_URL_REQUIRED");
        }
        String normalizedRef = SensitiveTextSanitizer.boundedNullableText(baseUrlRef, 128);
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
                normalizedRuntimeBaseUrl(environment.apiBaseUrl()),
                requestBaseUrlRefProvided ? "REQUEST_BASE_URL_REF" : "PLAN_BASE_URL_REF",
                normalizedRef,
                environment.code()
        );
    }

    /**
     * Mirrors WP6 run-target admission before claim renewal/account leasing so invalid runtime targets do not consume
     * the active queue claim or leak a lease that WP6 would reject before creating a run.
     */
    private String normalizedRuntimeBaseUrl(String rawBaseUrl) {
        String bounded = SensitiveTextSanitizer.boundedNullableText(rawBaseUrl, 512);
        if (!StringUtils.hasText(bounded)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "baseUrl 必填");
        }
        URI uri;
        try {
            uri = new URI(bounded);
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "baseUrl 必须是合法 HTTP/HTTPS URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!Set.of("http", "https").contains(scheme)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "baseUrl 仅支持 http/https");
        }
        if (StringUtils.hasText(uri.getRawUserInfo()) || StringUtils.hasText(uri.getRawQuery())
                || StringUtils.hasText(uri.getRawFragment())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "baseUrl 不允许携带 userInfo/query/fragment");
        }
        String host = normalizedHost(uri.getHost());
        if (!StringUtils.hasText(host)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "baseUrl 必须包含 host");
        }
        String path = StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "";
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        int port = uri.getPort();
        String authority = port > 0 ? host + ":" + port : host;
        return scheme + "://" + authority + path;
    }

    private String normalizedHost(String host) {
        if (!StringUtils.hasText(host)) {
            return "";
        }
        try {
            return IDN.toASCII(host.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return "";
        }
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
                        "keyword", SensitiveTextSanitizer.boundedNullableText(environmentKey, 128),
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
        String environmentId = SensitiveTextSanitizer.boundedNullableText(command.environmentId(), 128);
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

    private String valueText(Object value) {
        return value == null ? null : String.valueOf(value).trim();
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

    private String wp9StatusFromWp6(String wp6Status) {
        String normalized = StringUtils.hasText(wp6Status) ? wp6Status.trim().toUpperCase(java.util.Locale.ROOT) : "";
        return switch (normalized) {
            case "PASSED" -> "SUCCEEDED";
            case "TIMEOUT" -> "TIMEOUT";
            case "BLOCKED" -> "BLOCKED";
            default -> "FAILED";
        };
    }

    private String wp9StatusFromWp7(String wp7Status) {
        String normalized = StringUtils.hasText(wp7Status) ? wp7Status.trim().toUpperCase(java.util.Locale.ROOT) : "";
        return switch (normalized) {
            case "SUCCEEDED" -> "SUCCEEDED";
            case "TIMEOUT" -> "TIMEOUT";
            case "BLOCKED", "RUNNING", "QUEUED" -> "BLOCKED";
            default -> "FAILED";
        };
    }

    private boolean wp7TerminalStatus(String wp7Status) {
        String normalized = StringUtils.hasText(wp7Status) ? wp7Status.trim().toUpperCase(java.util.Locale.ROOT) : "";
        return Set.of("SUCCEEDED", "FAILED", "TIMEOUT", "CANCELED", "BLOCKED").contains(normalized);
    }

    private String sourceErrorCode(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().name();
        }
        return exception == null ? "RuntimeException" : exception.getClass().getSimpleName();
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

    private String wp7DispatchErrorCode(String wp9Status, UiE2eRunDetailResponse wp7Run) {
        if (SUCCESS_NODE_STATUSES.contains(wp9Status)) {
            return null;
        }
        if ("BLOCKED".equals(wp9Status) && !wp7TerminalStatus(wp7Run.status())) {
            return "EXECUTION_WP7_RUN_ASYNC_UNSUPPORTED";
        }
        if (StringUtils.hasText(wp7Run.failureCode())) {
            return wp7Run.failureCode();
        }
        return switch (wp9Status) {
            case "TIMEOUT" -> "EXECUTION_WP7_RUN_TIMEOUT";
            case "BLOCKED" -> "EXECUTION_WP7_RUN_BLOCKED";
            default -> "EXECUTION_WP7_RUN_FAILED";
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
                ? "sha256:" + SensitiveTextSanitizer.sha256Hex(preparation.baseUrlRef())
                : null);
        summary.put("runtimeCaseIdsProvided", preparation.runtimeCaseIdsProvided());
        summary.put("runtimeSecretRefsProvided", preparation.runtimeSecretRefsProvided());
        summary.put("runtimeSecretRefCount", preparation.secretRefs().size());
        summary.put("runtimeSecretRefDigests", preparation.secretRefs().stream()
                .map(secretRef -> "sha256:" + SensitiveTextSanitizer.sha256Hex(secretRef))
                .toList());
        summary.put("rawBaseUrlStored", false);
        summary.put("secretRefsStored", false);
        summary.put("rawOutputStored", false);
        summary.put("requestResponseStored", false);
        return summary;
    }

    private Map<String, Object> wp7DispatchSummary(
            UiE2eRunDetailResponse wp7Run,
            UiTestDispatchPreparation preparation,
            Instant completedAt
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Object> accountSummary = wp7Run.accountSummary() == null ? Map.of() : wp7Run.accountSummary();
        Map<String, Object> executionSummary = wp7Run.executionSummary() == null ? Map.of() : wp7Run.executionSummary();
        summary.put("completedStatus", wp9StatusFromWp7(wp7Run.status()));
        summary.put("completedAt", completedAt.toString());
        summary.put("runnerDispatched", true);
        summary.put("wp7DispatchReady", true);
        summary.put("wp7RunId", wp7Run.id().toString());
        summary.put("wp7Status", wp7Run.status());
        summary.put("wp7RunnerMode", wp7Run.runnerMode());
        summary.put("wp7TraceId", wp7Run.traceId());
        summary.put("wp7SceneId", wp7Run.sceneId().toString());
        summary.put("wp7SceneCode", wp7Run.sceneCode());
        summary.put("wp7SceneStatus", wp7Run.sceneStatus());
        summary.put("wp7BundleId", wp7Run.bundleId().toString());
        summary.put("wp7BundleStatus", wp7Run.bundleStatus());
        summary.put("wp7StepResultCount", wp7Run.stepResults() == null ? 0 : wp7Run.stepResults().size());
        summary.put("wp7ArtifactCount", wp7Run.artifacts() == null ? 0 : wp7Run.artifacts().size());
        summary.put("wp7FlakyStatus", wp7Run.flakyMark() == null ? null : wp7Run.flakyMark().status());
        summary.put("wp7StepStatusCounts", executionSummary.getOrDefault("stepStatusCounts", Map.of()));
        summary.put("wp7FailureBucketCounts", executionSummary.getOrDefault("failureBucketCounts", Map.of()));
        summary.put("wp7ArtifactTypes", executionSummary.getOrDefault("artifactTypes", List.of()));
        summary.put("wp7BaseUrlDigest", executionSummary.get("baseUrlDigest"));
        summary.put("baseUrlRefDigest", "sha256:" + SensitiveTextSanitizer.sha256Hex(preparation.baseUrlRef()));
        summary.put("accountLeaseRef", accountSummary.getOrDefault("accountLeaseRef", preparation.accountLeaseRef().toString()));
        summary.put("accountLeaseSecretRefDigest", accountSummary.get("secretRefDigest"));
        summary.put("wp7TerminalSnapshot", wp7TerminalStatus(wp7Run.status()));
        summary.put("wp7AsyncFollowUpRequired", !wp7TerminalStatus(wp7Run.status()));
        summary.put("rawBaseUrlStored", false);
        summary.put("secretRefPlaintextStored", false);
        summary.put("rawArtifactStored", false);
        summary.put("rawRunnerOutputStored", false);
        return summary;
    }

    private Map<String, Object> wp7DispatchSummary(
            UiE2eRunDetailResponse wp7Run,
            Instant completedAt
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Object> accountSummary = wp7Run.accountSummary() == null ? Map.of() : wp7Run.accountSummary();
        Map<String, Object> executionSummary = wp7Run.executionSummary() == null ? Map.of() : wp7Run.executionSummary();
        summary.put("completedStatus", wp9StatusFromWp7(wp7Run.status()));
        summary.put("completedAt", completedAt.toString());
        summary.put("runnerDispatched", true);
        summary.put("wp7DispatchReady", true);
        summary.put("wp7RunId", wp7Run.id().toString());
        summary.put("wp7Status", wp7Run.status());
        summary.put("wp7RunnerMode", wp7Run.runnerMode());
        summary.put("wp7TraceId", wp7Run.traceId());
        summary.put("wp7SceneId", wp7Run.sceneId().toString());
        summary.put("wp7SceneCode", wp7Run.sceneCode());
        summary.put("wp7SceneStatus", wp7Run.sceneStatus());
        summary.put("wp7BundleId", wp7Run.bundleId().toString());
        summary.put("wp7BundleStatus", wp7Run.bundleStatus());
        summary.put("wp7StepResultCount", wp7Run.stepResults() == null ? 0 : wp7Run.stepResults().size());
        summary.put("wp7ArtifactCount", wp7Run.artifacts() == null ? 0 : wp7Run.artifacts().size());
        summary.put("wp7FlakyStatus", wp7Run.flakyMark() == null ? null : wp7Run.flakyMark().status());
        summary.put("wp7StepStatusCounts", executionSummary.getOrDefault("stepStatusCounts", Map.of()));
        summary.put("wp7FailureBucketCounts", executionSummary.getOrDefault("failureBucketCounts", Map.of()));
        summary.put("wp7ArtifactTypes", executionSummary.getOrDefault("artifactTypes", List.of()));
        summary.put("wp7BaseUrlDigest", executionSummary.get("baseUrlDigest"));
        summary.put("accountLeaseRef", accountSummary.get("accountLeaseRef"));
        summary.put("accountLeaseSecretRefDigest", accountSummary.get("secretRefDigest"));
        summary.put("wp7TerminalSnapshot", wp7TerminalStatus(wp7Run.status()));
        summary.put("wp7AsyncFollowUpRequired", !wp7TerminalStatus(wp7Run.status()));
        summary.put("rawBaseUrlStored", false);
        summary.put("secretRefPlaintextStored", false);
        summary.put("rawArtifactStored", false);
        summary.put("rawRunnerOutputStored", false);
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

    interface TransactionBridge {
        <T> T inExecutionTransaction(Supplier<T> action);
    }
}
