package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.application.command.CreateApiRequest;
import com.songhg.veri.agent.asset.application.command.UpdateApiRequest;
import com.songhg.veri.agent.asset.application.command.UpdateAssetLifecycleRequest;
import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.application.query.AssetListQuery;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.view.ApiResponseDTO;
import com.songhg.veri.agent.asset.domain.AssetApi;
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
 * Owns API asset CRUD and status rules while AssetService remains a backward-compatible facade.
 */
@Service
public class AssetApiService {

    private static final Logger log = LoggerFactory.getLogger(AssetApiService.class);
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final Set<String> LIFECYCLE_STATUSES = AssetLifecycleStatus.codes();
    private static final Set<String> API_STATUSES = Set.of(STATUS_ACTIVE, "DEPRECATED", "REMOVED");
    private static final Set<String> API_HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final Map<String, Set<String>> API_STATUS_TRANSITIONS = Map.of(
            STATUS_ACTIVE, Set.of(STATUS_ACTIVE, "DEPRECATED"),
            "DEPRECATED", Set.of("DEPRECATED", "REMOVED"),
            "REMOVED", Set.of("REMOVED")
    );

    private final AssetRepository repository;
    private final AssetProjectAuditService projectAuditService;
    private final AssetLifecycleService lifecycleService;

    public AssetApiService(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService,
            AssetLifecycleService lifecycleService
    ) {
        this.repository = repository;
        this.projectAuditService = projectAuditService;
        this.lifecycleService = lifecycleService;
    }

    /**
     * Resolves the owning project for resource-level authorization, including archived or soft-deleted APIs.
     */
    public String apiProjectScopeId(UUID id) {
        AssetApi api = repository.apiIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API 不存在: " + id));
        return projectAuditService.projectContext(api.projectId()).projectId();
    }

    /**
     * Lists active APIs by default and validates project scope before reading scoped assets.
     */
    public com.songhg.veri.agent.common.api.PageResponse<ApiResponseDTO> listApis(AssetListRequest request) {
        projectAuditService.validateProjectWhenProvided(request.getProjectId());
        AssetListQuery query = assetListQuery(request);
        List<ApiResponseDTO> items = repository.apis(query).stream()
                .map(AssetResponseMapper::toApiResponse)
                .toList();
        return com.songhg.veri.agent.common.api.PageResponse.of(items, query.index(), query.size(), repository.countApis(query));
    }

    /**
     * Reads only non-deleted APIs for normal detail pages and editing flows.
     */
    public ApiResponseDTO getApi(UUID id) {
        return repository.api(id)
                .map(AssetResponseMapper::toApiResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + id));
    }

    /**
     * Reads lifecycle-visible API details after validating the inactive asset's project is accessible.
     */
    public ApiResponseDTO getApiIncludingInactive(UUID id) {
        AssetApi api = repository.apiIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + id));
        projectAuditService.validateProjectWhenProvided(api.projectId());
        return AssetResponseMapper.toApiResponse(api);
    }

    /**
     * Creates a manual API and enforces project-local uniqueness on method plus path before writing audit.
     */
    public ApiResponseDTO createApi(CreateApiRequest request) {
        String scopeId = projectAuditService.projectContext(request.projectId()).projectId();
        String httpMethod = valueIn(request.httpMethod(), null, API_HTTP_METHODS, "httpMethod");
        if (repository.hasActiveApiPathConflict(request.projectId(), request.path(), httpMethod, UUID.randomUUID())) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下 API 路径和方法已存在");
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AssetApi api = new AssetApi(
                id,
                assetCode("API", id),
                request.summary(),
                request.description(),
                httpMethod,
                request.path(),
                "MANUAL",
                null,
                trimToNull(request.version()),
                request.requestSchema(),
                request.responseSchema(),
                request.projectId(),
                initialStatus(request.status()),
                "ACTIVE",
                null,
                null,
                now,
                now
        );
        projectAuditService.writeProjectAudit("CREATE", "API", id, scopeId);
        repository.saveApi(api);
        log.info("Created api id={}, summary={}, trace_id={}", id, request.summary(), TraceContext.getTraceId());
        return AssetResponseMapper.toApiResponse(api);
    }

    /**
     * Updates API metadata while preserving source identity and rejecting illegal status regressions with audit evidence.
     */
    public ApiResponseDTO updateApi(UUID id, UpdateApiRequest request) {
        AssetApi existing = repository.api(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + id));
        Instant now = Instant.now();
        String httpMethod = valueIn(request.httpMethod(), null, API_HTTP_METHODS, "httpMethod");
        if (repository.hasActiveApiPathConflict(existing.projectId(), request.path(), httpMethod, id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下 API 路径和方法已存在");
        }
        String nextStatus = nextStatus(
                "API",
                id,
                existing.projectId(),
                existing.status(),
                request.status(),
                API_STATUSES,
                API_STATUS_TRANSITIONS
        );
        AssetApi updated = new AssetApi(
                id,
                existing.code(),
                request.summary(),
                request.description(),
                httpMethod,
                request.path(),
                existing.source(),
                existing.sourceRef(),
                valueOrDefault(request.version(), existing.version()),
                request.requestSchema(),
                request.responseSchema(),
                existing.projectId(),
                nextStatus,
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
        projectAuditService.writeProjectAudit("UPDATE", "API", id, existing.projectId());
        AssetApi stored = repository.saveApi(updated);
        return AssetResponseMapper.toApiResponse(stored);
    }

    /**
     * Delegates lifecycle transitions to the shared lifecycle service so archive/delete/restore rules stay consistent.
     */
    public ApiResponseDTO updateApiLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        return lifecycleService.updateApiLifecycle(id, request);
    }

    /**
     * Centralizes API review-state transition checks so denied transitions always leave audit evidence.
     */
    private String nextStatus(
            String resourceType,
            UUID resourceId,
            String projectId,
            String currentStatus,
            String rawNextStatus,
            Set<String> allowedStatuses,
            Map<String, Set<String>> transitions
    ) {
        String nextStatus = valueIn(rawNextStatus, currentStatus, allowedStatuses, "status");
        if (!transitions.getOrDefault(currentStatus, Set.of(currentStatus)).contains(nextStatus)) {
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
        return valueIn(rawValue, "ACTIVE", LIFECYCLE_STATUSES, "lifecycleStatus");
    }

    private static String initialStatus(String rawValue) {
        return valueIn(rawValue, "ACTIVE", API_STATUSES, "status");
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

    private static String valueOrDefault(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }
}
