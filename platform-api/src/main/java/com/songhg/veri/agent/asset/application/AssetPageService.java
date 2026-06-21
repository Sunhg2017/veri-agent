package com.songhg.veri.agent.asset.application;

import static com.songhg.veri.agent.asset.application.AssetCodeGenerator.assetCode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.command.CreatePageRequest;
import com.songhg.veri.agent.asset.application.command.RollbackAssetVersionRequest;
import com.songhg.veri.agent.asset.application.command.UpdateAssetLifecycleRequest;
import com.songhg.veri.agent.asset.application.command.UpdatePageRequest;
import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.application.query.AssetListQuery;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.view.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.application.view.PageResponse;
import com.songhg.veri.agent.asset.domain.AssetLifecycleStatus;
import com.songhg.veri.agent.asset.domain.AssetPage;
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
 * Owns page asset CRUD, JSON normalization and page review-state transition rules.
 */
@Service
public class AssetPageService {

    private static final Logger log = LoggerFactory.getLogger(AssetPageService.class);
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final Set<String> LIFECYCLE_STATUSES = AssetLifecycleStatus.codes();
    private static final Set<String> PAGE_STATUSES = Set.of(STATUS_ACTIVE, "DEPRECATED");
    private static final Set<String> PAGE_SOURCES = Set.of("FIGMA", "LANHU", "AXURE", SOURCE_MANUAL);
    private static final Map<String, Set<String>> PAGE_STATUS_TRANSITIONS = Map.of(
            STATUS_ACTIVE, Set.of(STATUS_ACTIVE, "DEPRECATED"),
            "DEPRECATED", Set.of("DEPRECATED")
    );

    private final AssetRepository repository;
    private final AssetProjectAuditService projectAuditService;
    private final AssetVersionHistoryService versionHistoryService;
    private final AssetVersionRollbackService versionRollbackService;
    private final AssetLifecycleService lifecycleService;
    private final ObjectMapper objectMapper;

    public AssetPageService(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService,
            AssetVersionHistoryService versionHistoryService,
            AssetVersionRollbackService versionRollbackService,
            AssetLifecycleService lifecycleService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.projectAuditService = projectAuditService;
        this.versionHistoryService = versionHistoryService;
        this.versionRollbackService = versionRollbackService;
        this.lifecycleService = lifecycleService;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves project ownership from active or inactive pages for resource-level authorization.
     */
    public String pageProjectScopeId(UUID id) {
        AssetPage page = repository.pageIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面不存在: " + id));
        return projectAuditService.projectContext(page.projectId()).projectId();
    }

    /**
     * Lists active pages by default after validating the optional project scope.
     */
    public com.songhg.veri.agent.common.api.PageResponse<PageResponse> listPages(AssetListRequest request) {
        projectAuditService.validateProjectWhenProvided(request.getProjectId());
        AssetListQuery query = assetListQuery(request);
        List<PageResponse> items = repository.pages(query).stream()
                .map(AssetResponseMapper::toPageResponse)
                .toList();
        return com.songhg.veri.agent.common.api.PageResponse.of(items, query.index(), query.size(), repository.countPages(query));
    }

    /**
     * Reads non-deleted pages for normal detail and editing flows.
     */
    public PageResponse getPage(UUID id) {
        return repository.page(id)
                .map(AssetResponseMapper::toPageResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + id));
    }

    /**
     * Reads lifecycle-visible page details and validates the page's project scope.
     */
    public PageResponse getPageIncludingInactive(UUID id) {
        AssetPage page = repository.pageIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + id));
        projectAuditService.validateProjectWhenProvided(page.projectId());
        return AssetResponseMapper.toPageResponse(page);
    }

    /**
     * Creates a manual or prototype-backed page while serializing component trees consistently.
     */
    public PageResponse createPage(CreatePageRequest request) {
        String scopeId = projectAuditService.projectContext(request.projectId()).projectId();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AssetPage page = new AssetPage(
                id,
                assetCode("PAGE", id),
                request.name(),
                request.urlPattern(),
                valueIn(request.source(), SOURCE_MANUAL, PAGE_SOURCES, "source"),
                request.sourceRef(),
                trimToNull(request.sourceVersion()),
                jsonValue(request.componentTree()),
                request.screenshotUrl(),
                request.projectId(),
                initialStatus(request.status()),
                STATUS_ACTIVE,
                null,
                null,
                now,
                now
        );
        projectAuditService.writeProjectAudit("CREATE", "PAGE", id, scopeId);
        AssetPage stored = repository.savePage(page);
        versionHistoryService.recordPageCreated(stored);
        log.info("Created page id={}, name={}, trace_id={}", id, request.name(), TraceContext.getTraceId());
        return AssetResponseMapper.toPageResponse(stored);
    }

    /**
     * Updates page metadata and rejects illegal ACTIVE/DEPRECATED transitions with audit evidence.
     */
    public PageResponse updatePage(UUID id, UpdatePageRequest request) {
        AssetPage existing = repository.page(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + id));
        Instant now = Instant.now();
        String nextStatus = nextStatus(
                "PAGE",
                id,
                existing.projectId(),
                existing.status(),
                request.status()
        );
        AssetPage updated = new AssetPage(
                id,
                existing.code(),
                request.name(),
                request.urlPattern(),
                valueIn(request.source(), existing.source(), PAGE_SOURCES, "source"),
                request.sourceRef(),
                trimToNull(request.sourceVersion()),
                jsonValue(request.componentTree()),
                request.screenshotUrl(),
                existing.projectId(),
                nextStatus,
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
        projectAuditService.writeProjectAudit("UPDATE", "PAGE", id, existing.projectId());
        AssetPage stored = repository.savePage(updated);
        versionHistoryService.recordPageChange(existing, stored, "UPDATE");
        return AssetResponseMapper.toPageResponse(stored);
    }

    /**
     * Returns immutable version history for active or inactive pages.
     */
    public List<AssetVersionHistoryResponse> pageVersions(UUID id) {
        AssetPage page = repository.pageIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + id));
        return versionHistoryService.responses("PAGE", page.id());
    }

    /**
     * Delegates rollback to the shared rollback service so snapshot restore rules stay consistent.
     */
    public PageResponse rollbackPageVersion(UUID id, int version, RollbackAssetVersionRequest request) {
        return versionRollbackService.rollbackPageVersion(id, version, request);
    }

    /**
     * Delegates lifecycle transitions to the shared lifecycle service so page rules stay aligned.
     */
    public PageResponse updatePageLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        return lifecycleService.updatePageLifecycle(id, request);
    }

    /**
     * Centralizes page status transition checks so denied transitions always leave audit evidence.
     */
    private String nextStatus(
            String resourceType,
            UUID resourceId,
            String projectId,
            String currentStatus,
            String rawNextStatus
    ) {
        String nextStatus = valueIn(rawNextStatus, currentStatus, PAGE_STATUSES, "status");
        if (!PAGE_STATUS_TRANSITIONS.getOrDefault(currentStatus, Set.of(currentStatus)).contains(nextStatus)) {
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
        return valueIn(rawValue, STATUS_ACTIVE, PAGE_STATUSES, "status");
    }

    private static String valueIn(String rawValue, String defaultValue, Set<String> allowedValues, String fieldName) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (!allowedValues.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不合法: " + rawValue);
        }
        return value;
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
