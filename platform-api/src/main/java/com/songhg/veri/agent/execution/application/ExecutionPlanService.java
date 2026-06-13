package com.songhg.veri.agent.execution.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.execution.application.command.CreateExecutionPlanCommand;
import com.songhg.veri.agent.execution.application.command.ExecutionDagCommand;
import com.songhg.veri.agent.execution.application.command.UpdateExecutionPlanCommand;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.query.ExecutionPlanPageRequest;
import com.songhg.veri.agent.execution.application.query.ExecutionPlanQuery;
import com.songhg.veri.agent.execution.application.view.ExecutionDryRunResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionPlanDetailResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionPlanNodeResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionPlanSummaryResponse;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ExecutionPlanService {

    private static final Set<String> PLAN_STATUSES = Set.of("DRAFT", "READY", "DISABLED", "ARCHIVED");
    private static final Set<String> UPDATE_ALLOWED_STATUSES = Set.of("DRAFT", "READY", "DISABLED");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ExecutionRepository repository;
    private final ExecutionDagValidator dagValidator;
    private final ExecutionPlatformContextClient contextClient;
    private final ExecutionActorResolver actorResolver;
    private final ObjectMapper objectMapper;

    public ExecutionPlanService(
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

    @Transactional(noRollbackFor = BusinessException.class)
    public ExecutionPlanDetailResponse createPlan(CreateExecutionPlanCommand command) {
        PlatformContext context = contextClient.projectContext(command.projectId());
        String projectId = context.resourceId();
        String status = normalizeStatus(command.status(), "DRAFT");
        Instant now = Instant.now();
        UUID planId = UUID.randomUUID();
        ExecutionDagValidationResult validation = dagValidator.validate(planId, projectId, command.dag(), now);
        if (!validation.valid()) {
            throw dagInvalid(validation);
        }
        String actor = actorResolver.currentActor();
        ExecutionPlan plan = new ExecutionPlan(
                planId,
                projectId,
                boundedText(command.name(), 128),
                status,
                boundedText(command.environmentKey(), 128),
                json(command.triggerPolicy() == null ? Map.of("manualEnabled", true) : command.triggerPolicy()),
                validation.dagDigest(),
                boundedNullableText(command.description(), 512),
                actor,
                actor,
                null,
                now,
                now
        );
        repository.insertPlan(plan);
        repository.replacePlanNodes(planId, validation.nodes());
        auditPlan(plan, "execution.plan.created", "SUCCESS", Map.of(
                "status", plan.status(),
                "nodeCount", validation.nodes().size(),
                "dagDigest", plan.dagDigest()
        ));
        return detail(plan, validation.nodes());
    }

    @Transactional(readOnly = true)
    public PageResponse<ExecutionPlanSummaryResponse> plans(ExecutionPlanPageRequest request) {
        ExecutionPlanQuery query = normalizeQuery(request.toQuery());
        List<ExecutionPlan> plans = repository.plans(query);
        Map<UUID, Integer> nodeCounts = plans.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ExecutionPlan::id,
                        plan -> repository.planNodes(plan.id()).size()
                ));
        List<ExecutionPlanSummaryResponse> items = plans.stream()
                .map(plan -> toSummary(plan, nodeCounts.getOrDefault(plan.id(), 0)))
                .toList();
        return PageResponse.of(items, request.getIndex(), request.getSize(), repository.countPlans(query));
    }

    @Transactional(readOnly = true)
    public ExecutionPlanDetailResponse plan(UUID id) {
        ExecutionPlan plan = requirePlan(id);
        return detail(plan, repository.planNodes(id));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ExecutionPlanDetailResponse updatePlan(UUID id, UpdateExecutionPlanCommand command) {
        ExecutionPlan existing = requirePlan(id);
        assertPlanMutable(existing);
        Instant now = Instant.now();
        String status = normalizeStatus(command.status(), existing.status());
        ExecutionDagCommand dag = command.dag();
        List<ExecutionPlanNode> nodes = repository.planNodes(id);
        String dagDigest = existing.dagDigest();
        ExecutionDagValidationResult validation = null;
        if (dag != null) {
            validation = dagValidator.validate(id, existing.projectId(), dag, now);
            if (!validation.valid()) {
                throw dagInvalid(validation);
            }
            nodes = validation.nodes();
            dagDigest = validation.dagDigest();
        }
        if ("READY".equals(status)) {
            validateReadyState(validation, id, existing.projectId(), now);
        }
        ExecutionPlan updated = new ExecutionPlan(
                existing.id(),
                existing.projectId(),
                StringUtils.hasText(command.name()) ? boundedText(command.name(), 128) : existing.name(),
                status,
                StringUtils.hasText(command.environmentKey())
                        ? boundedText(command.environmentKey(), 128)
                        : existing.environmentKey(),
                command.triggerPolicy() == null ? existing.triggerPolicyJson() : json(command.triggerPolicy()),
                dagDigest,
                command.description() == null ? existing.description() : boundedNullableText(command.description(), 512),
                existing.createdBy(),
                actorResolver.currentActor(),
                existing.archivedAt(),
                existing.createdAt(),
                now
        );
        repository.updatePlan(updated);
        if (dag != null) {
            repository.replacePlanNodes(id, nodes);
        }
        auditPlan(updated, "execution.plan.updated", "SUCCESS", Map.of(
                "status", updated.status(),
                "nodeCount", nodes.size(),
                "dagDigest", updated.dagDigest()
        ));
        return detail(updated, nodes);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ExecutionPlanDetailResponse archivePlan(UUID id) {
        ExecutionPlan existing = requirePlan(id);
        if ("ARCHIVED".equals(existing.status())) {
            return detail(existing, repository.planNodes(id));
        }
        Instant now = Instant.now();
        ExecutionPlan archived = new ExecutionPlan(
                existing.id(),
                existing.projectId(),
                existing.name(),
                "ARCHIVED",
                existing.environmentKey(),
                existing.triggerPolicyJson(),
                existing.dagDigest(),
                existing.description(),
                existing.createdBy(),
                actorResolver.currentActor(),
                now,
                existing.createdAt(),
                now
        );
        repository.archivePlan(archived);
        auditPlan(archived, "execution.plan.archived", "SUCCESS", Map.of(
                "status", archived.status(),
                "dagDigest", archived.dagDigest()
        ));
        return detail(archived, repository.planNodes(id));
    }

    @Transactional(readOnly = true)
    public ExecutionDryRunResponse dryRun(UUID id) {
        ExecutionPlan plan = requirePlan(id);
        ExecutionDagValidationResult validation = dagValidator.validate(
                id,
                plan.projectId(),
                toDagCommand(repository.planNodes(id)),
                Instant.now()
        );
        return new ExecutionDryRunResponse(
                plan.id(),
                validation.valid(),
                validation.dagDigest(),
                validation.nodePolicies(),
                validation.issues(),
                dryRunPolicy(false)
        );
    }

    public String planProjectScopeId(UUID id) {
        return repository.planProjectScopeId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行计划不存在"));
    }

    /**
     * READY is the only triggerable plan status, so every transition into READY repeats DAG validation.
     *
     * <p>Updates that do not replace DAG nodes still dry-run the stored DAG representation; this prevents a plan from
     * becoming triggerable after an external resource was archived or moved to another project.</p>
     */
    private void validateReadyState(
            ExecutionDagValidationResult validation,
            UUID planId,
            String projectId,
            Instant now
    ) {
        ExecutionDagValidationResult effectiveValidation = validation;
        if (effectiveValidation == null) {
            effectiveValidation = dagValidator.validate(planId, projectId, toDagCommand(repository.planNodes(planId)), now);
        }
        if (!effectiveValidation.valid()) {
            throw dagInvalid(effectiveValidation);
        }
    }

    private ExecutionDagCommand toDagCommand(List<ExecutionPlanNode> nodes) {
        return new ExecutionDagCommand(nodes.stream()
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

    private ExecutionPlanDetailResponse detail(ExecutionPlan plan, List<ExecutionPlanNode> nodes) {
        return new ExecutionPlanDetailResponse(
                plan.id(),
                plan.projectId(),
                plan.name(),
                plan.status(),
                plan.environmentKey(),
                plan.description(),
                plan.dagDigest(),
                readMap(plan.triggerPolicyJson()),
                nodes.stream()
                        .map(this::toNodeResponse)
                        .toList(),
                plan.createdBy(),
                plan.updatedBy(),
                plan.archivedAt(),
                plan.createdAt(),
                plan.updatedAt()
        );
    }

    private ExecutionPlanNodeResponse toNodeResponse(ExecutionPlanNode node) {
        return new ExecutionPlanNodeResponse(
                node.id(),
                node.nodeKey(),
                node.nodeType(),
                node.dependencyKeys(),
                readMap(node.inputSummaryJson()),
                node.failurePolicy(),
                node.timeoutSeconds(),
                readMap(node.retryPolicyJson()),
                node.createdAt(),
                node.updatedAt()
        );
    }

    private ExecutionPlanSummaryResponse toSummary(ExecutionPlan plan, int nodeCount) {
        return new ExecutionPlanSummaryResponse(
                plan.id(),
                plan.projectId(),
                plan.name(),
                plan.status(),
                plan.environmentKey(),
                plan.description(),
                plan.dagDigest(),
                nodeCount,
                plan.archivedAt(),
                plan.createdAt(),
                plan.updatedAt()
        );
    }

    private ExecutionPlan requirePlan(UUID id) {
        return repository.plan(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行计划不存在"));
    }

    private void assertPlanMutable(ExecutionPlan plan) {
        if ("ARCHIVED".equals(plan.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_PLAN_ARCHIVED: 已归档计划不可更新");
        }
    }

    private BusinessException dagInvalid(ExecutionDagValidationResult validation) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "EXECUTION_DAG_INVALID: " + validation.issues().stream()
                        .findFirst()
                        .map(com.songhg.veri.agent.execution.application.view.ExecutionValidationIssueResponse::code)
                        .orElse("UNKNOWN")
        );
    }

    private ExecutionPlanQuery normalizeQuery(ExecutionPlanQuery query) {
        String projectId = query.projectId();
        if (StringUtils.hasText(projectId)) {
            projectId = contextClient.projectContext(projectId).resourceId();
        }
        return new ExecutionPlanQuery(projectId, query.status(), query.keyword(), query.limit(), query.offset());
    }

    private String normalizeStatus(String value, String defaultStatus) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : defaultStatus;
        if (!PLAN_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "EXECUTION_PLAN_STATUS_INVALID");
        }
        if (!UPDATE_ALLOWED_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "EXECUTION_PLAN_ARCHIVE_ENDPOINT_REQUIRED");
        }
        return normalized;
    }

    private Map<String, Object> dryRunPolicy(boolean transientPlan) {
        return Map.ofEntries(
                Map.entry("dryRun", true),
                Map.entry("transientPlan", transientPlan),
                Map.entry("runCreated", false),
                Map.entry("runnerDispatched", false),
                Map.entry("rawRunnerOutputStored", false),
                Map.entry("secretPlaintextStored", false)
        );
    }

    private void auditPlan(
            ExecutionPlan plan,
            String action,
            String result,
            Map<String, Object> afterJson
    ) {
        contextClient.writeAuditEvent(action, "EXECUTION_PLAN", plan.id().toString(), plan.projectId(), result, afterJson);
    }

    private String boundedText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请求字段不能为空");
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
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
