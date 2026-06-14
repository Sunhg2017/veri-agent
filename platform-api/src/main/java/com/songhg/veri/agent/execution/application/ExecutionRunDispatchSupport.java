package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.apiautomation.application.ApiAutomationService;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationRunCommand;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunResultResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.util.StringUtils;

/**
 * Owns WP9 claimed API_TEST dispatch into WP6 while keeping runtime secrets and base URLs out of WP9 storage.
 */
final class ExecutionRunDispatchSupport {

    private static final Set<String> COMPLETABLE_NODE_STATUSES = Set.of(
            "SUCCEEDED", "SKIPPED", "FAILED", "TIMEOUT", "BLOCKED"
    );
    private static final Set<String> SUCCESS_NODE_STATUSES = Set.of("SUCCEEDED", "SKIPPED");

    private final ExecutionRepository repository;
    private final ApiAutomationService apiAutomationService;
    private final ManagementStore managementStore;
    private final ExecutionProperties properties;
    private final ExecutionRunJsonSupport jsonSupport;
    private final ExecutionRunQueueSupport queueSupport;
    private final ExecutionRunResponseMapper responseMapper;
    private final TransactionBridge transactionBridge;

    ExecutionRunDispatchSupport(
            ExecutionRepository repository,
            ApiAutomationService apiAutomationService,
            ManagementStore managementStore,
            ExecutionProperties properties,
            ExecutionRunJsonSupport jsonSupport,
            ExecutionRunQueueSupport queueSupport,
            ExecutionRunResponseMapper responseMapper,
            TransactionBridge transactionBridge
    ) {
        this.repository = repository;
        this.apiAutomationService = apiAutomationService;
        this.managementStore = managementStore;
        this.properties = properties;
        this.jsonSupport = jsonSupport;
        this.queueSupport = queueSupport;
        this.responseMapper = responseMapper;
        this.transactionBridge = transactionBridge;
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
        ApiAutomationRunDetailResponse wp6Run = apiAutomationService.createRun(new CreateApiAutomationRunCommand(
                preparation.bundleId(),
                preparation.environmentId(),
                preparation.baseUrl(),
                preparation.caseIds(),
                preparation.timeoutSeconds(),
                preparation.secretRefs()
        ));
        return transactionBridge.inExecutionTransaction(
                () -> completeDispatchedApiTestNodeRun(preparation, wp6Run)
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
        return queueSupport.aggregateRunAfterNodeCompletion(completed.runId(), completedAt);
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
            return new ResolvedDispatchTarget(runtimeBaseUrl, "REQUEST_BASE_URL", null, null);
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
                SensitiveTextSanitizer.boundedNullableText(environment.apiBaseUrl(), 512),
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

    private String wp9StatusFromWp6(String wp6Status) {
        String normalized = StringUtils.hasText(wp6Status) ? wp6Status.trim().toUpperCase(java.util.Locale.ROOT) : "";
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
}
