package com.songhg.veri.agent.asset.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.api.request.AssetListRequest;
import com.songhg.veri.agent.asset.api.request.CreateBusinessFlowRequest;
import com.songhg.veri.agent.asset.api.request.UpdateAssetLifecycleRequest;
import com.songhg.veri.agent.asset.api.request.UpdateBusinessFlowRequest;
import com.songhg.veri.agent.asset.api.response.BusinessFlowResponse;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetLifecycleStatus;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Owns business flow asset CRUD, flow JSON normalization and review-state transition rules.
 */
@Service
public class AssetBusinessFlowService {

    private static final Logger log = LoggerFactory.getLogger(AssetBusinessFlowService.class);
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final Set<String> LIFECYCLE_STATUSES = AssetLifecycleStatus.codes();
    private static final Set<String> PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<String> FLOW_STATUSES = Set.of(STATUS_DRAFT, STATUS_ACTIVE, "ARCHIVED");
    private static final Map<String, Set<String>> FLOW_STATUS_TRANSITIONS = Map.of(
            STATUS_DRAFT, Set.of(STATUS_DRAFT, STATUS_ACTIVE, "ARCHIVED"),
            STATUS_ACTIVE, Set.of(STATUS_ACTIVE, "ARCHIVED"),
            "ARCHIVED", Set.of("ARCHIVED")
    );

    private final AssetRepository repository;
    private final AssetProjectAuditService projectAuditService;
    private final AssetLifecycleService lifecycleService;
    private final ObjectMapper objectMapper;

    public AssetBusinessFlowService(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService,
            AssetLifecycleService lifecycleService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.projectAuditService = projectAuditService;
        this.lifecycleService = lifecycleService;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves project ownership from active or inactive flows for resource-level authorization.
     */
    public String businessFlowProjectScopeId(UUID id) {
        AssetBusinessFlow flow = repository.businessFlowIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流不存在: " + id));
        return projectAuditService.projectContext(flow.projectId()).projectId();
    }

    /**
     * Lists active business flows by default after validating the optional project scope.
     */
    public com.songhg.veri.agent.common.api.PageResponse<BusinessFlowResponse> listBusinessFlows(AssetListRequest request) {
        projectAuditService.validateProjectWhenProvided(request.getProjectId());
        AssetListQuery query = assetListQuery(request);
        List<BusinessFlowResponse> items = repository.businessFlows(query).stream()
                .map(AssetResponseMapper::toBusinessFlowResponse)
                .toList();
        return com.songhg.veri.agent.common.api.PageResponse.of(items, query.index(), query.size(), repository.countBusinessFlows(query));
    }

    /**
     * Reads non-deleted business flows for normal detail and editing flows.
     */
    public BusinessFlowResponse getBusinessFlow(UUID id) {
        return repository.businessFlow(id)
                .map(AssetResponseMapper::toBusinessFlowResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流资产不存在: " + id));
    }

    /**
     * Reads lifecycle-visible business flow details and validates the flow's project scope.
     */
    public BusinessFlowResponse getBusinessFlowIncludingInactive(UUID id) {
        AssetBusinessFlow flow = repository.businessFlowIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流资产不存在: " + id));
        projectAuditService.validateProjectWhenProvided(flow.projectId());
        return AssetResponseMapper.toBusinessFlowResponse(flow);
    }

    /**
     * Creates a business flow and stores flowJson as the canonical JSON/string payload.
     */
    public BusinessFlowResponse createBusinessFlow(CreateBusinessFlowRequest request) {
        String scopeId = projectAuditService.projectContext(request.projectId()).projectId();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AssetBusinessFlow flow = new AssetBusinessFlow(
                id,
                assetCode("FLOW", id),
                request.name(),
                request.description(),
                jsonValue(request.flowJson()),
                valueIn(request.priority(), "MEDIUM", PRIORITIES, "priority"),
                request.projectId(),
                initialStatus(request.status()),
                STATUS_ACTIVE,
                null,
                null,
                now,
                now
        );
        projectAuditService.writeProjectAudit("CREATE", "BUSINESS_FLOW", id, scopeId);
        repository.saveBusinessFlow(flow);
        log.info("Created business flow id={}, name={}, trace_id={}", id, request.name(), TraceContext.getTraceId());
        return AssetResponseMapper.toBusinessFlowResponse(flow);
    }

    /**
     * Updates business flow metadata and rejects illegal DRAFT/ACTIVE/ARCHIVED transitions with audit evidence.
     */
    public BusinessFlowResponse updateBusinessFlow(UUID id, UpdateBusinessFlowRequest request) {
        AssetBusinessFlow existing = repository.businessFlow(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流资产不存在: " + id));
        Instant now = Instant.now();
        String nextStatus = nextStatus(
                "BUSINESS_FLOW",
                id,
                existing.projectId(),
                existing.status(),
                request.status()
        );
        AssetBusinessFlow updated = new AssetBusinessFlow(
                id,
                existing.code(),
                request.name(),
                request.description(),
                jsonValue(request.flowJson()),
                valueIn(request.priority(), existing.priority(), PRIORITIES, "priority"),
                existing.projectId(),
                nextStatus,
                AssetLifecycleStatus.normalize(existing.lifecycleStatus(), existing.deletedAt()),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
        projectAuditService.writeProjectAudit("UPDATE", "BUSINESS_FLOW", id, existing.projectId());
        AssetBusinessFlow stored = repository.saveBusinessFlow(updated);
        return AssetResponseMapper.toBusinessFlowResponse(stored);
    }

    /**
     * Delegates lifecycle transitions to the shared lifecycle service so flow rules stay aligned.
     */
    public BusinessFlowResponse updateBusinessFlowLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        return lifecycleService.updateBusinessFlowLifecycle(id, request);
    }

    /**
     * Centralizes business flow status checks so denied transitions always leave audit evidence.
     */
    private String nextStatus(
            String resourceType,
            UUID resourceId,
            String projectId,
            String currentStatus,
            String rawNextStatus
    ) {
        String nextStatus = valueIn(rawNextStatus, currentStatus, FLOW_STATUSES, "status");
        if (!FLOW_STATUS_TRANSITIONS.getOrDefault(currentStatus, Set.of(currentStatus)).contains(nextStatus)) {
            projectAuditService.writeProjectAudit("STATUS_CHANGE_DENIED", resourceType, resourceId, projectId, "DENIED");
            throw new BusinessException(
                    ErrorCode.INVALID_STATE,
                    resourceType + " 状态不允许从 " + currentStatus + " 变更为 " + nextStatus
            );
        }
        return nextStatus;
    }

    private static AssetListQuery assetListQuery(AssetListRequest request) {
        return new AssetListQuery(
                trimToNull(request.getProjectId()),
                lifecycleFilter(request.getLifecycleStatus()),
                request.getStatus(),
                request.getSource(),
                request.getKeyword(),
                request.toPageQuery()
        );
    }

    private static String lifecycleFilter(String rawValue) {
        return valueIn(rawValue, STATUS_ACTIVE, LIFECYCLE_STATUSES, "lifecycleStatus");
    }

    private static String initialStatus(String rawValue) {
        return valueIn(rawValue, STATUS_DRAFT, FLOW_STATUSES, "status");
    }

    private static String valueIn(String rawValue, String defaultValue, Set<String> allowedValues, String fieldName) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (!allowedValues.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不合法: " + rawValue);
        }
        return value;
    }

    private static String assetCode(String prefix, UUID id) {
        return prefix + "-" + id.toString().replace("-", "").substring(0, 12);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String jsonValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return StringUtils.hasText(text) ? text : null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JSON 字段格式不合法");
        }
    }
}
