package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.api.request.AssetListRequest;
import com.songhg.veri.agent.asset.api.request.AssetExportRequest;
import com.songhg.veri.agent.asset.api.request.AssetImportRequest;
import com.songhg.veri.agent.asset.api.request.AssetPrototypeSyncRequest;
import com.songhg.veri.agent.asset.api.request.CreateApiRequest;
import com.songhg.veri.agent.asset.api.request.CreateBusinessFlowRequest;
import com.songhg.veri.agent.asset.api.request.CreateLinkRequest;
import com.songhg.veri.agent.asset.api.request.CreatePageRequest;
import com.songhg.veri.agent.asset.api.request.CreateRequirementRequest;
import com.songhg.veri.agent.asset.api.request.RollbackAssetVersionRequest;
import com.songhg.veri.agent.asset.api.request.CreateTestCaseRequest;
import com.songhg.veri.agent.asset.api.request.TraceLinkListRequest;
import com.songhg.veri.agent.asset.api.request.UpdateApiRequest;
import com.songhg.veri.agent.asset.api.request.UpdateAssetLifecycleRequest;
import com.songhg.veri.agent.asset.api.request.UpdateBusinessFlowRequest;
import com.songhg.veri.agent.asset.api.request.UpdatePageRequest;
import com.songhg.veri.agent.asset.api.request.UpdateRequirementRequest;
import com.songhg.veri.agent.asset.api.request.UpdateTestCaseRequest;
import com.songhg.veri.agent.asset.api.request.UpdateTestCaseStepsRequest;
import com.songhg.veri.agent.asset.api.response.ApiResponseDTO;
import com.songhg.veri.agent.asset.api.response.AssetExportPayload;
import com.songhg.veri.agent.asset.api.response.AssetImpactAnalysisResponse;
import com.songhg.veri.agent.asset.api.response.AssetImpactNodeResponse;
import com.songhg.veri.agent.asset.api.response.AssetImportItemResponse;
import com.songhg.veri.agent.asset.api.response.AssetImportResponse;
import com.songhg.veri.agent.asset.api.response.AssetPrototypeSyncResponse;
import com.songhg.veri.agent.asset.api.response.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.api.response.BusinessFlowResponse;
import com.songhg.veri.agent.asset.api.response.PageResponse;
import com.songhg.veri.agent.asset.api.response.RequirementResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseStepResponse;
import com.songhg.veri.agent.asset.api.response.TraceLinkResponse;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.AssetReviewStatus;
import com.songhg.veri.agent.asset.domain.AssetVersionHistory;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.asset.domain.TraceLink;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);
    private static final String ASSET_REQUIREMENT = "REQUIREMENT";
    private static final String ASSET_API = "API";
    private static final String ASSET_PAGE = "PAGE";
    private static final String ASSET_BUSINESS_FLOW = "BUSINESS_FLOW";
    private static final String ASSET_TEST_CASE = "TEST_CASE";
    private static final String SUBJECT_FLOW = "FLOW";
    private static final String SUBJECT_CASE = "CASE";
    private static final String FORMAT_CSV = "CSV";
    private static final String FORMAT_JSON = "JSON";
    private static final String FORMAT_OPENAPI = "OPENAPI";
    private static final String SOURCE_IMPORT = "IMPORT";
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PLANNED = "PLANNED";
    private static final Set<String> REVIEW_STATUSES = AssetReviewStatus.codes();
    private static final Set<String> LIFECYCLE_STATUSES = Set.of(STATUS_ACTIVE, "ARCHIVED", "DELETED");
    private static final Set<String> PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<String> REQUIREMENT_SOURCES = Set.of(SOURCE_IMPORT, SOURCE_MANUAL);
    private static final Set<String> API_STATUSES = Set.of(STATUS_ACTIVE, "DEPRECATED", "REMOVED");
    private static final Set<String> API_SOURCES = Set.of(FORMAT_OPENAPI, SOURCE_MANUAL, SOURCE_IMPORT);
    private static final Set<String> API_HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final Set<String> PAGE_STATUSES = Set.of(STATUS_ACTIVE, "DEPRECATED");
    private static final Set<String> PAGE_SOURCES = Set.of("FIGMA", "LANHU", "AXURE", SOURCE_MANUAL);
    private static final Set<String> PROTOTYPE_SOURCES = Set.of("FIGMA", "LANHU", "AXURE");
    private static final Set<String> FLOW_STATUSES = Set.of(STATUS_DRAFT, STATUS_ACTIVE, "ARCHIVED");
    private static final Set<String> IMPACT_SUBJECT_TYPES = Set.of(
            ASSET_REQUIREMENT,
            ASSET_API,
            ASSET_PAGE,
            SUBJECT_FLOW,
            SUBJECT_CASE
    );
    private static final Map<String, String> IMPACT_SUBJECT_TYPE_ALIASES = Map.of(
            ASSET_BUSINESS_FLOW, SUBJECT_FLOW,
            ASSET_TEST_CASE, SUBJECT_CASE
    );
    private static final Set<String> IMPORT_EXPORT_FORMATS = Set.of(FORMAT_CSV, FORMAT_JSON, FORMAT_OPENAPI);
    private static final Map<String, Set<String>> IMPORT_EXPORT_FORMATS_BY_ASSET_TYPE = Map.of(
            ASSET_REQUIREMENT, Set.of(FORMAT_CSV, FORMAT_JSON),
            ASSET_API, IMPORT_EXPORT_FORMATS,
            ASSET_TEST_CASE, Set.of(FORMAT_CSV, FORMAT_JSON)
    );
    private static final Set<String> IMPORT_EXPORT_ASSET_TYPES = IMPORT_EXPORT_FORMATS_BY_ASSET_TYPE.keySet();
    private static final Map<String, Set<String>> API_STATUS_TRANSITIONS = Map.of(
            STATUS_ACTIVE, Set.of(STATUS_ACTIVE, "DEPRECATED"),
            "DEPRECATED", Set.of("DEPRECATED", "REMOVED"),
            "REMOVED", Set.of("REMOVED")
    );
    private static final Map<String, Set<String>> PAGE_STATUS_TRANSITIONS = Map.of(
            STATUS_ACTIVE, Set.of(STATUS_ACTIVE, "DEPRECATED"),
            "DEPRECATED", Set.of("DEPRECATED")
    );
    private static final Map<String, Set<String>> FLOW_STATUS_TRANSITIONS = Map.of(
            STATUS_DRAFT, Set.of(STATUS_DRAFT, STATUS_ACTIVE, "ARCHIVED"),
            STATUS_ACTIVE, Set.of(STATUS_ACTIVE, "ARCHIVED"),
            "ARCHIVED", Set.of("ARCHIVED")
    );

    private final AssetRepository repository;
    private final PlatformContextClient contextClient;
    private final ObjectMapper objectMapper;

    public AssetService(AssetRepository repository, PlatformContextClient contextClient) {
        this(repository, contextClient, new ObjectMapper().findAndRegisterModules());
    }

    @Autowired
    public AssetService(AssetRepository repository, PlatformContextClient contextClient, ObjectMapper objectMapper) {
        this.repository = repository;
        this.contextClient = contextClient;
        this.objectMapper = objectMapper;
    }

    public String resolveProjectScopeId(String projectId) {
        return projectContext(projectId).projectId();
    }

    public String requirementProjectScopeId(UUID id) {
        return resolveProjectScopeId(repository.requirementIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id))
                .projectId());
    }

    public String apiProjectScopeId(UUID id) {
        return resolveProjectScopeId(repository.apiIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API 不存在: " + id))
                .projectId());
    }

    public String pageProjectScopeId(UUID id) {
        return resolveProjectScopeId(repository.pageIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面不存在: " + id))
                .projectId());
    }

    public String businessFlowProjectScopeId(UUID id) {
        return resolveProjectScopeId(repository.businessFlowIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流不存在: " + id))
                .projectId());
    }

    public String testCaseProjectScopeId(UUID id) {
        return resolveProjectScopeId(repository.testCaseIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id))
                .projectId());
    }

    // ---- Requirements ----

    public com.songhg.veri.agent.common.api.PageResponse<RequirementResponse> listRequirements(AssetListRequest request) {
        validateProjectWhenProvided(request.getProjectId());
        AssetListQuery query = assetListQuery(request);
        List<RequirementResponse> items = repository.requirements(query).stream()
                .map(AssetService::toRequirementResponse)
                .toList();
        return com.songhg.veri.agent.common.api.PageResponse.of(items, query.index(), query.size(), repository.countRequirements(query));
    }

    public RequirementResponse getRequirement(UUID id) {
        return repository.requirement(id)
                .map(AssetService::toRequirementResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
    }

    public RequirementResponse getRequirementIncludingInactive(UUID id) {
        AssetRequirement requirement = repository.requirementIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
        validateProjectWhenProvided(requirement.projectId());
        return toRequirementResponse(requirement);
    }

    public Optional<RequirementResponse> findImportedRequirement(String projectId, String sourceRef) {
        if (!StringUtils.hasText(projectId) || !StringUtils.hasText(sourceRef)) {
            return Optional.empty();
        }
        validateProjectWhenProvided(projectId);
        return repository.requirementBySourceRef(projectId, "IMPORT", sourceRef.trim())
                .map(AssetService::toRequirementResponse);
    }

    @Transactional
    public RequirementResponse createRequirement(CreateRequirementRequest request) {
        String scopeId = projectContext(request.projectId()).projectId();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String source = valueIn(request.source(), "MANUAL", REQUIREMENT_SOURCES, "source");
        String sourceRef = trimToNull(request.sourceRef());
        if ("IMPORT".equals(source) && sourceRef != null) {
            Optional<AssetRequirement> existing = repository.requirementBySourceRef(request.projectId(), source, sourceRef);
            if (existing.isPresent()) {
                AssetRequirement merged = mergeImportedRequirement(existing.get(), request, now);
                if (sameRequirement(existing.get(), merged)) {
                    return toRequirementResponse(existing.get());
                }
                if (!"DRAFT".equals(existing.get().status())) {
                    writeProjectAudit("UPSERT_DENIED", "REQUIREMENT", existing.get().id(), scopeId, "DENIED");
                    throw new BusinessException(
                            ErrorCode.INVALID_STATE,
                            "既有导入需求已进入评审或审批状态，需人工处理差异后再更新"
                    );
                }
                writeProjectAudit("UPSERT", "REQUIREMENT", merged.id(), scopeId);
                VersionDiff diff = requirementDiff(existing.get(), merged);
                AssetRequirement stored = repository.saveRequirement(merged);
                saveRequirementHistory(stored, "UPSERT", diff);
                log.info("Updated imported requirement id={}, sourceRef={}, trace_id={}",
                        stored.id(), sourceRef, TraceContext.getTraceId());
                return toRequirementResponse(stored);
            }
        }
        AssetRequirement req = new AssetRequirement(
                id,
                assetCode("REQ", id),
                request.title(),
                request.description(),
                source,
                sourceRef,
                trimToNull(request.sourceUrl()),
                trimToNull(request.acceptanceCriteria()),
                initialStatus(request.status(), "DRAFT", "REQUIREMENT"),
                valueIn(request.priority(), "MEDIUM", PRIORITIES, "priority"),
                request.projectId(),
                request.tags(),
                1,
                "ACTIVE",
                null,
                null,
                now,
                now
        );
        writeProjectAudit("CREATE", "REQUIREMENT", id, scopeId);
        AssetRequirement stored = repository.saveRequirement(req);
        if (stored.id().equals(id)) {
            saveRequirementHistory(stored, "CREATE", VersionDiff.empty());
            log.info("Created requirement id={}, title={}, trace_id={}", id, request.title(), TraceContext.getTraceId());
        }
        return toRequirementResponse(stored);
    }

    private AssetRequirement mergeImportedRequirement(
            AssetRequirement existing,
            CreateRequirementRequest request,
            Instant now
    ) {
        return new AssetRequirement(
                existing.id(),
                existing.code(),
                request.title(),
                trimToNull(request.description()),
                existing.source(),
                existing.sourceRef(),
                trimToNull(request.sourceUrl()),
                trimToNull(request.acceptanceCriteria()),
                existing.status(),
                valueIn(request.priority(), existing.priority(), PRIORITIES, "priority"),
                existing.projectId(),
                mergeTags(existing.tags(), request.tags()),
                existing.version() + 1,
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
    }

    private static boolean sameRequirement(AssetRequirement left, AssetRequirement right) {
        return java.util.Objects.equals(left.title(), right.title())
                && java.util.Objects.equals(left.description(), right.description())
                && java.util.Objects.equals(left.sourceUrl(), right.sourceUrl())
                && java.util.Objects.equals(left.acceptanceCriteria(), right.acceptanceCriteria())
                && java.util.Objects.equals(left.status(), right.status())
                && java.util.Objects.equals(left.priority(), right.priority())
                && java.util.Objects.equals(normalizedTags(left.tags()), normalizedTags(right.tags()));
    }

    @Transactional
    public RequirementResponse updateRequirement(UUID id, UpdateRequirementRequest request) {
        AssetRequirement existing = repository.requirement(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
        Instant now = Instant.now();
        String nextStatus = nextRequirementStatus(existing, request.status());
        AssetRequirement updated = new AssetRequirement(
                id,
                existing.code(),
                request.title(),
                request.description(),
                existing.source(),
                existing.sourceRef(),
                existing.sourceUrl(),
                existing.acceptanceCriteria(),
                nextStatus,
                valueIn(request.priority(), existing.priority(), PRIORITIES, "priority"),
                existing.projectId(),
                request.tags(),
                existing.version() + 1,
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
        writeProjectAudit("UPDATE", "REQUIREMENT", id, existing.projectId());
        AssetRequirement stored = repository.saveRequirement(updated);
        saveRequirementHistory(stored, "UPDATE", requirementDiff(existing, stored));
        return toRequirementResponse(stored);
    }

    public List<AssetVersionHistoryResponse> requirementVersions(UUID id) {
        AssetRequirement requirement = repository.requirementIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
        return repository.assetVersionHistory("REQUIREMENT", requirement.id()).stream()
                .map(this::toVersionHistoryResponse)
                .toList();
    }

    @Transactional
    public RequirementResponse rollbackRequirementVersion(
            UUID id,
            int version,
            RollbackAssetVersionRequest request
    ) {
        AssetRequirement existing = repository.requirementIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
        AssetVersionHistory history = findVersionHistory("REQUIREMENT", id, version);
        JsonNode snapshot = jsonNode(history.snapshotJson());
        Instant now = Instant.now();
        AssetRequirement rollback = new AssetRequirement(
                existing.id(),
                existing.code(),
                requiredSnapshotText(snapshot, "title"),
                snapshotText(snapshot, "description"),
                existing.source(),
                existing.sourceRef(),
                snapshotText(snapshot, "sourceUrl"),
                snapshotText(snapshot, "acceptanceCriteria"),
                valueIn(snapshotText(snapshot, "status"), existing.status(), REVIEW_STATUSES, "status"),
                valueIn(snapshotText(snapshot, "priority"), existing.priority(), PRIORITIES, "priority"),
                existing.projectId(),
                snapshotText(snapshot, "tags"),
                existing.version() + 1,
                lifecycleStatus(snapshotText(snapshot, "lifecycleStatus"), snapshotInstant(snapshot, "deletedAt")),
                snapshotInstant(snapshot, "archivedAt"),
                snapshotInstant(snapshot, "deletedAt"),
                existing.createdAt(),
                now
        );
        if ("ACTIVE".equals(lifecycleStatus(rollback.lifecycleStatus(), rollback.deletedAt()))) {
            ensureRequirementRestoreHasNoConflict(rollback);
        }
        writeProjectAudit("ROLLBACK", "REQUIREMENT", id, existing.projectId());
        AssetRequirement stored = repository.saveRequirement(rollback);
        saveRequirementHistory(stored, "ROLLBACK", requirementDiff(existing, stored));
        log.info("Rolled back requirement id={} to version={}, reason={}, trace_id={}",
                id, version, trimToNull(request == null ? null : request.reason()), TraceContext.getTraceId());
        return toRequirementResponse(stored);
    }

    @Transactional
    public RequirementResponse updateRequirementLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        AssetRequirement existing = repository.requirementIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
        Instant now = Instant.now();
        String nextLifecycle = nextLifecycle(
                "REQUIREMENT",
                id,
                existing.projectId(),
                lifecycleStatus(existing.lifecycleStatus(), existing.deletedAt()),
                request.lifecycleStatus()
        );
        if ("ACTIVE".equals(nextLifecycle)) {
            ensureRequirementRestoreHasNoConflict(existing);
        }
        AssetRequirement updated = new AssetRequirement(
                existing.id(),
                existing.code(),
                existing.title(),
                existing.description(),
                existing.source(),
                existing.sourceRef(),
                existing.sourceUrl(),
                existing.acceptanceCriteria(),
                existing.status(),
                existing.priority(),
                existing.projectId(),
                existing.tags(),
                existing.version() + 1,
                nextLifecycle,
                archivedAtFor(nextLifecycle, existing.archivedAt(), now),
                deletedAtFor(nextLifecycle, now),
                existing.createdAt(),
                now
        );
        writeProjectAudit(lifecycleAction(nextLifecycle), "REQUIREMENT", id, existing.projectId());
        AssetRequirement stored = repository.saveRequirement(updated);
        saveRequirementHistory(stored, lifecycleAction(nextLifecycle), requirementDiff(existing, stored));
        return toRequirementResponse(stored);
    }

    // ---- APIs ----

    public com.songhg.veri.agent.common.api.PageResponse<ApiResponseDTO> listApis(AssetListRequest request) {
        validateProjectWhenProvided(request.getProjectId());
        AssetListQuery query = assetListQuery(request);
        List<ApiResponseDTO> items = repository.apis(query).stream()
                .map(AssetService::toApiResponse)
                .toList();
        return com.songhg.veri.agent.common.api.PageResponse.of(items, query.index(), query.size(), repository.countApis(query));
    }

    public ApiResponseDTO getApi(UUID id) {
        return repository.api(id)
                .map(AssetService::toApiResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + id));
    }

    public ApiResponseDTO getApiIncludingInactive(UUID id) {
        AssetApi api = repository.apiIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + id));
        validateProjectWhenProvided(api.projectId());
        return toApiResponse(api);
    }

    public ApiResponseDTO createApi(CreateApiRequest request) {
        String scopeId = projectContext(request.projectId()).projectId();
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
                initialStatus(request.status(), "ACTIVE", "API"),
                "ACTIVE",
                null,
                null,
                now,
                now
        );
        writeProjectAudit("CREATE", "API", id, scopeId);
        repository.saveApi(api);
        log.info("Created api id={}, summary={}, trace_id={}", id, request.summary(), TraceContext.getTraceId());
        return toApiResponse(api);
    }

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
        writeProjectAudit("UPDATE", "API", id, existing.projectId());
        repository.saveApi(updated);
        return toApiResponse(updated);
    }

    public ApiResponseDTO updateApiLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        AssetApi existing = repository.apiIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + id));
        Instant now = Instant.now();
        String nextLifecycle = nextLifecycle(
                "API",
                id,
                existing.projectId(),
                lifecycleStatus(existing.lifecycleStatus(), existing.deletedAt()),
                request.lifecycleStatus()
        );
        if ("ACTIVE".equals(nextLifecycle)) {
            ensureApiRestoreHasNoConflict(existing);
        }
        AssetApi updated = new AssetApi(
                existing.id(),
                existing.code(),
                existing.summary(),
                existing.description(),
                existing.httpMethod(),
                existing.path(),
                existing.source(),
                existing.sourceRef(),
                existing.version(),
                existing.requestSchema(),
                existing.responseSchema(),
                existing.projectId(),
                existing.status(),
                nextLifecycle,
                archivedAtFor(nextLifecycle, existing.archivedAt(), now),
                deletedAtFor(nextLifecycle, now),
                existing.createdAt(),
                now
        );
        writeProjectAudit(lifecycleAction(nextLifecycle), "API", id, existing.projectId());
        repository.saveApi(updated);
        return toApiResponse(updated);
    }

    // ---- Pages ----

    public com.songhg.veri.agent.common.api.PageResponse<PageResponse> listPages(AssetListRequest request) {
        validateProjectWhenProvided(request.getProjectId());
        AssetListQuery query = assetListQuery(request);
        List<PageResponse> items = repository.pages(query).stream()
                .map(AssetService::toPageResponse)
                .toList();
        return com.songhg.veri.agent.common.api.PageResponse.of(items, query.index(), query.size(), repository.countPages(query));
    }

    public PageResponse getPage(UUID id) {
        return repository.page(id)
                .map(AssetService::toPageResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + id));
    }

    public PageResponse getPageIncludingInactive(UUID id) {
        AssetPage page = repository.pageIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + id));
        validateProjectWhenProvided(page.projectId());
        return toPageResponse(page);
    }

    public PageResponse createPage(CreatePageRequest request) {
        String scopeId = projectContext(request.projectId()).projectId();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AssetPage page = new AssetPage(
                id,
                assetCode("PAGE", id),
                request.name(),
                request.urlPattern(),
                valueIn(request.source(), "MANUAL", PAGE_SOURCES, "source"),
                request.sourceRef(),
                trimToNull(request.sourceVersion()),
                jsonValue(request.componentTree()),
                request.screenshotUrl(),
                request.projectId(),
                initialStatus(request.status(), "ACTIVE", "PAGE"),
                "ACTIVE",
                null,
                null,
                now,
                now
        );
        writeProjectAudit("CREATE", "PAGE", id, scopeId);
        repository.savePage(page);
        log.info("Created page id={}, name={}, trace_id={}", id, request.name(), TraceContext.getTraceId());
        return toPageResponse(page);
    }

    public PageResponse updatePage(UUID id, UpdatePageRequest request) {
        AssetPage existing = repository.page(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + id));
        Instant now = Instant.now();
        String nextStatus = nextStatus(
                "PAGE",
                id,
                existing.projectId(),
                existing.status(),
                request.status(),
                PAGE_STATUSES,
                PAGE_STATUS_TRANSITIONS
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
        writeProjectAudit("UPDATE", "PAGE", id, existing.projectId());
        repository.savePage(updated);
        return toPageResponse(updated);
    }

    public PageResponse updatePageLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        AssetPage existing = repository.pageIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + id));
        Instant now = Instant.now();
        String nextLifecycle = nextLifecycle(
                "PAGE",
                id,
                existing.projectId(),
                lifecycleStatus(existing.lifecycleStatus(), existing.deletedAt()),
                request.lifecycleStatus()
        );
        if ("ACTIVE".equals(nextLifecycle)) {
            ensurePageRestoreHasNoConflict(existing);
        }
        AssetPage updated = new AssetPage(
                existing.id(),
                existing.code(),
                existing.name(),
                existing.urlPattern(),
                existing.source(),
                existing.sourceRef(),
                existing.sourceVersion(),
                existing.componentTree(),
                existing.screenshotUrl(),
                existing.projectId(),
                existing.status(),
                nextLifecycle,
                archivedAtFor(nextLifecycle, existing.archivedAt(), now),
                deletedAtFor(nextLifecycle, now),
                existing.createdAt(),
                now
        );
        writeProjectAudit(lifecycleAction(nextLifecycle), "PAGE", id, existing.projectId());
        repository.savePage(updated);
        return toPageResponse(updated);
    }

    // ---- Business Flows ----

    public com.songhg.veri.agent.common.api.PageResponse<BusinessFlowResponse> listBusinessFlows(AssetListRequest request) {
        validateProjectWhenProvided(request.getProjectId());
        AssetListQuery query = assetListQuery(request);
        List<BusinessFlowResponse> items = repository.businessFlows(query).stream()
                .map(AssetService::toBusinessFlowResponse)
                .toList();
        return com.songhg.veri.agent.common.api.PageResponse.of(items, query.index(), query.size(), repository.countBusinessFlows(query));
    }

    public BusinessFlowResponse getBusinessFlow(UUID id) {
        return repository.businessFlow(id)
                .map(AssetService::toBusinessFlowResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流资产不存在: " + id));
    }

    public BusinessFlowResponse getBusinessFlowIncludingInactive(UUID id) {
        AssetBusinessFlow flow = repository.businessFlowIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流资产不存在: " + id));
        validateProjectWhenProvided(flow.projectId());
        return toBusinessFlowResponse(flow);
    }

    public BusinessFlowResponse createBusinessFlow(CreateBusinessFlowRequest request) {
        String scopeId = projectContext(request.projectId()).projectId();
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
                initialStatus(request.status(), "DRAFT", "BUSINESS_FLOW"),
                "ACTIVE",
                null,
                null,
                now,
                now
        );
        writeProjectAudit("CREATE", "BUSINESS_FLOW", id, scopeId);
        repository.saveBusinessFlow(flow);
        log.info("Created business flow id={}, name={}, trace_id={}", id, request.name(), TraceContext.getTraceId());
        return toBusinessFlowResponse(flow);
    }

    public BusinessFlowResponse updateBusinessFlow(UUID id, UpdateBusinessFlowRequest request) {
        AssetBusinessFlow existing = repository.businessFlow(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流资产不存在: " + id));
        Instant now = Instant.now();
        String nextStatus = nextStatus(
                "BUSINESS_FLOW",
                id,
                existing.projectId(),
                existing.status(),
                request.status(),
                FLOW_STATUSES,
                FLOW_STATUS_TRANSITIONS
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
                lifecycleStatus(existing.lifecycleStatus(), existing.deletedAt()),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
        writeProjectAudit("UPDATE", "BUSINESS_FLOW", id, existing.projectId());
        repository.saveBusinessFlow(updated);
        return toBusinessFlowResponse(updated);
    }

    public BusinessFlowResponse updateBusinessFlowLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        AssetBusinessFlow existing = repository.businessFlowIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流资产不存在: " + id));
        Instant now = Instant.now();
        String nextLifecycle = nextLifecycle(
                "BUSINESS_FLOW",
                id,
                existing.projectId(),
                existing.lifecycleStatus(),
                request.lifecycleStatus()
        );
        if ("ACTIVE".equals(nextLifecycle)) {
            ensureBusinessFlowRestoreHasNoConflict(existing);
        }
        AssetBusinessFlow updated = new AssetBusinessFlow(
                existing.id(),
                existing.code(),
                existing.name(),
                existing.description(),
                existing.flowJson(),
                existing.priority(),
                existing.projectId(),
                existing.status(),
                nextLifecycle,
                archivedAtFor(nextLifecycle, existing.archivedAt(), now),
                deletedAtFor(nextLifecycle, now),
                existing.createdAt(),
                now
        );
        writeProjectAudit(lifecycleAction(nextLifecycle), "BUSINESS_FLOW", id, existing.projectId());
        repository.saveBusinessFlow(updated);
        return toBusinessFlowResponse(updated);
    }

    // ---- Test Cases ----

    public com.songhg.veri.agent.common.api.PageResponse<TestCaseResponse> listTestCases(AssetListRequest request) {
        validateProjectWhenProvided(request.getProjectId());
        AssetListQuery query = assetListQuery(request);
        List<TestCaseResponse> items = repository.testCases(query).stream()
                .map(tc -> toTestCaseResponse(tc, tc.steps()))
                .toList();
        return com.songhg.veri.agent.common.api.PageResponse.of(items, query.index(), query.size(), repository.countTestCases(query));
    }

    public TestCaseResponse getTestCase(UUID id) {
        TestCaseRecord testCase = repository.testCase(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        return toTestCaseResponse(testCase, testCase.steps());
    }

    public TestCaseResponse getTestCaseIncludingInactive(UUID id) {
        TestCaseRecord testCase = repository.testCaseIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        validateProjectWhenProvided(testCase.projectId());
        return toTestCaseResponse(testCase, testCase.steps());
    }

    @Transactional
    public TestCaseResponse createTestCase(CreateTestCaseRequest request) {
        String scopeId = projectContext(request.projectId()).projectId();
        validateRequirementBelongsToProject(request.requirementId(), request.projectId());
        validateApiBelongsToProject(request.apiId(), request.projectId());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        List<CreateTestCaseRequest.StepDto> requestedSteps = Optional.ofNullable(request.steps())
                .orElse(Collections.emptyList());
        List<TestCaseStep> steps = new java.util.ArrayList<>();
        for (int i = 0; i < requestedSteps.size(); i++) {
            CreateTestCaseRequest.StepDto step = requestedSteps.get(i);
            steps.add(new TestCaseStep(UUID.randomUUID(), id, i, step.action(), step.expectedResult()));
        }
        TestCaseRecord tc = new TestCaseRecord(
                id,
                assetCode("TC", id),
                request.title(),
                request.description(),
                request.projectId(),
                request.requirementId(),
                request.apiId(),
                "MANUAL",
                null,
                initialStatus(request.status(), "DRAFT", "TEST_CASE"),
                valueIn(request.priority(), "MEDIUM", PRIORITIES, "priority"),
                request.tags(),
                steps,
                1,
                "ACTIVE",
                null,
                null,
                now,
                now
        );
        writeProjectAudit("CREATE", "TEST_CASE", id, scopeId);
        TestCaseRecord stored = repository.saveTestCase(tc);
        saveTestCaseHistory(stored, "CREATE", VersionDiff.empty());
        log.info("Created test case id={}, title={}, trace_id={}", id, request.title(), TraceContext.getTraceId());
        return toTestCaseResponse(stored, steps);
    }

    @Transactional
    public TestCaseResponse updateTestCase(UUID id, UpdateTestCaseRequest request) {
        TestCaseRecord existing = repository.testCase(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        validateRequirementBelongsToProject(request.requirementId(), existing.projectId());
        validateApiBelongsToProject(request.apiId(), existing.projectId());
        List<TestCaseStep> existingSteps = existing.steps();
        Instant now = Instant.now();
        String nextStatus = nextTestCaseStatus(existing, request.status());
        TestCaseRecord updated = new TestCaseRecord(
                id,
                existing.code(),
                request.title(),
                request.description(),
                existing.projectId(),
                request.requirementId(),
                request.apiId(),
                existing.source(),
                existing.sourceRef(),
                nextStatus,
                valueIn(request.priority(), existing.priority(), PRIORITIES, "priority"),
                request.tags(),
                existingSteps,
                existing.version() + 1,
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
        writeProjectAudit("UPDATE", "TEST_CASE", id, existing.projectId());
        TestCaseRecord stored = repository.saveTestCase(updated);
        saveTestCaseHistory(stored, "UPDATE", testCaseDiff(existing, stored));
        return toTestCaseResponse(stored, existingSteps);
    }

    public List<AssetVersionHistoryResponse> testCaseVersions(UUID id) {
        TestCaseRecord testCase = repository.testCaseIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        return repository.assetVersionHistory("TEST_CASE", testCase.id()).stream()
                .map(this::toVersionHistoryResponse)
                .toList();
    }

    @Transactional
    public TestCaseResponse rollbackTestCaseVersion(
            UUID id,
            int version,
            RollbackAssetVersionRequest request
    ) {
        TestCaseRecord existing = repository.testCaseIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        AssetVersionHistory history = findVersionHistory("TEST_CASE", id, version);
        JsonNode snapshot = jsonNode(history.snapshotJson());
        Instant now = Instant.now();
        TestCaseRecord rollback = new TestCaseRecord(
                existing.id(),
                existing.code(),
                requiredSnapshotText(snapshot, "title"),
                snapshotText(snapshot, "description"),
                existing.projectId(),
                snapshotUuid(snapshot, "requirementId"),
                snapshotUuid(snapshot, "apiId"),
                existing.source(),
                existing.sourceRef(),
                valueIn(snapshotText(snapshot, "status"), existing.status(), REVIEW_STATUSES, "status"),
                valueIn(snapshotText(snapshot, "priority"), existing.priority(), PRIORITIES, "priority"),
                snapshotText(snapshot, "tags"),
                snapshotSteps(snapshot.path("steps"), id),
                existing.version() + 1,
                lifecycleStatus(snapshotText(snapshot, "lifecycleStatus"), snapshotInstant(snapshot, "deletedAt")),
                snapshotInstant(snapshot, "archivedAt"),
                snapshotInstant(snapshot, "deletedAt"),
                existing.createdAt(),
                now
        );
        validateRequirementBelongsToProject(rollback.requirementId(), rollback.projectId());
        validateApiBelongsToProject(rollback.apiId(), rollback.projectId());
        if ("ACTIVE".equals(lifecycleStatus(rollback.lifecycleStatus(), rollback.deletedAt()))) {
            ensureTestCaseRestoreHasNoConflict(rollback);
        }
        writeProjectAudit("ROLLBACK", "TEST_CASE", id, existing.projectId());
        TestCaseRecord stored = repository.saveTestCase(rollback);
        saveTestCaseHistory(stored, "ROLLBACK", testCaseDiff(existing, stored));
        log.info("Rolled back test case id={} to version={}, reason={}, trace_id={}",
                id, version, trimToNull(request == null ? null : request.reason()), TraceContext.getTraceId());
        return toTestCaseResponse(stored, stored.steps());
    }

    @Transactional
    public TestCaseResponse updateTestCaseLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        TestCaseRecord existing = repository.testCaseIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        Instant now = Instant.now();
        String nextLifecycle = nextLifecycle(
                "TEST_CASE",
                id,
                existing.projectId(),
                lifecycleStatus(existing.lifecycleStatus(), existing.deletedAt()),
                request.lifecycleStatus()
        );
        if ("ACTIVE".equals(nextLifecycle)) {
            ensureTestCaseRestoreHasNoConflict(existing);
        }
        TestCaseRecord updated = new TestCaseRecord(
                existing.id(),
                existing.code(),
                existing.title(),
                existing.description(),
                existing.projectId(),
                existing.requirementId(),
                existing.apiId(),
                existing.source(),
                existing.sourceRef(),
                existing.status(),
                existing.priority(),
                existing.tags(),
                existing.steps(),
                existing.version() + 1,
                nextLifecycle,
                archivedAtFor(nextLifecycle, existing.archivedAt(), now),
                deletedAtFor(nextLifecycle, now),
                existing.createdAt(),
                now
        );
        writeProjectAudit(lifecycleAction(nextLifecycle), "TEST_CASE", id, existing.projectId());
        TestCaseRecord stored = repository.saveTestCase(updated);
        saveTestCaseHistory(stored, lifecycleAction(nextLifecycle), testCaseDiff(existing, stored));
        return toTestCaseResponse(stored, stored.steps());
    }

    // ---- Test Case Steps ----

    public List<TestCaseStepResponse> listTestCaseSteps(UUID caseId) {
        getTestCase(caseId);
        List<TestCaseStep> steps = repository.testCaseSteps(caseId);
        return steps.stream()
                .sorted(Comparator.comparingInt(TestCaseStep::stepOrder))
                .map(s -> new TestCaseStepResponse(s.stepOrder(), s.action(), s.expectedResult()))
                .toList();
    }

    @Transactional
    public List<TestCaseStepResponse> updateTestCaseSteps(UUID caseId, UpdateTestCaseStepsRequest request) {
        TestCaseRecord existing = repository.testCase(caseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + caseId));
        List<TestCaseStep> steps = new java.util.ArrayList<>();
        for (int i = 0; i < request.steps().size(); i++) {
            UpdateTestCaseStepsRequest.StepItem item = request.steps().get(i);
            steps.add(new TestCaseStep(UUID.randomUUID(), caseId, i, item.action(), item.expectedResult()));
        }
        writeProjectAudit("UPDATE", "TEST_CASE_STEPS", caseId, existing.projectId());
        Instant now = Instant.now();
        TestCaseRecord updated = new TestCaseRecord(
                existing.id(),
                existing.code(),
                existing.title(),
                existing.description(),
                existing.projectId(),
                existing.requirementId(),
                existing.apiId(),
                existing.source(),
                existing.sourceRef(),
                existing.status(),
                existing.priority(),
                existing.tags(),
                steps,
                existing.version() + 1,
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
        TestCaseRecord stored = repository.saveTestCase(updated);
        saveTestCaseHistory(stored, "STEPS_UPDATE", testCaseDiff(existing, stored));
        return steps.stream()
                .map(s -> new TestCaseStepResponse(s.stepOrder(), s.action(), s.expectedResult()))
                .toList();
    }

    // ---- Trace Links ----

    public com.songhg.veri.agent.common.api.PageResponse<TraceLinkResponse> listLinks(TraceLinkListRequest request) {
        List<TraceLinkResponse> filtered = repository.traceLinks(
                        request.getRequirementId(),
                        request.getApiId(),
                        request.getPageId(),
                        request.getFlowId(),
                        request.getCaseId()
                ).stream()
                .map(AssetService::toTraceLinkResponse)
                .toList();
        return page(filtered, request.getIndex(), request.getSize());
    }

    public TraceLinkResponse createLink(CreateLinkRequest request) {
        AssetRequirement requirement = repository.requirement(request.requirementId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + request.requirementId()));
        if (request.apiId() == null && request.pageId() == null && request.flowId() == null && request.caseId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "追踪链接至少需要一个目标资产");
        }
        validateApiBelongsToProject(request.apiId(), requirement.projectId());
        validatePageBelongsToProject(request.pageId(), requirement.projectId());
        validateBusinessFlowBelongsToProject(request.flowId(), requirement.projectId());
        validateTestCaseBelongsToProject(request.caseId(), requirement.projectId());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        TraceLink link = new TraceLink(
                id,
                request.requirementId(),
                request.apiId(),
                request.pageId(),
                request.flowId(),
                request.caseId(),
                now
        );
        writeProjectAudit("CREATE", "TRACE_LINK", id, requirement.projectId());
        repository.saveTraceLink(link);
        log.info("Created trace link id={}, requirementId={}, trace_id={}",
                id, request.requirementId(), TraceContext.getTraceId());
        return toTraceLinkResponse(link);
    }

    // ---- Import / Export ----

    public AssetImportResponse importAssets(AssetImportRequest request) {
        String assetType = importExportAssetType(request.assetType());
        String format = importExportFormat(assetType, request.format(), "导入");
        validateProjectWhenProvided(request.projectId());
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());
        List<Map<String, String>> rows = parseImportRows(assetType, format, request.content());
        writeAssetBatchAudit(dryRun ? "IMPORT_DRY_RUN" : "IMPORT", "ASSET_" + assetType, request.projectId(), "SUCCEEDED");
        List<AssetImportItemResponse> items = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ImportPlan plan = planImportRow(assetType, request.projectId(), rows.get(i), i + 1);
            items.add(dryRun || !STATUS_PLANNED.equals(plan.status())
                    ? plan.toResponse()
                    : applyImportPlan(assetType, request.projectId(), rows.get(i), plan));
        }
        return toImportResponse(assetType, format, dryRun, rows.size(), items);
    }

    public AssetExportPayload exportAssets(AssetExportRequest request) {
        String assetType = importExportAssetType(request.getAssetType());
        String format = importExportFormat(assetType, request.getFormat(), "导出");
        String projectId = trimToNull(request.getProjectId());
        validateProjectWhenProvided(projectId);
        String content = switch (assetType) {
            case ASSET_REQUIREMENT -> exportRequirements(request, format);
            case ASSET_API -> exportApis(request, format);
            case ASSET_TEST_CASE -> exportTestCases(request, format);
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "assetType 不合法: " + assetType);
        };
        writeAssetBatchAudit("EXPORT", "ASSET_" + assetType, projectId, "SUCCEEDED");
        String extension = FORMAT_OPENAPI.equals(format) ? "json" : format.toLowerCase(Locale.ROOT);
        String contentType = FORMAT_CSV.equals(format) ? "text/csv;charset=UTF-8" : "application/json;charset=UTF-8";
        return new AssetExportPayload(
                "wp3-" + assetType.toLowerCase(Locale.ROOT).replace("_", "-") + "." + extension,
                contentType,
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    // ---- Prototype sync / impact analysis ----

    @Transactional
    public AssetPrototypeSyncResponse syncPrototypePages(AssetPrototypeSyncRequest request) {
        String source = valueIn(request.source(), null, PROTOTYPE_SOURCES, "source");
        String projectId = projectContext(request.projectId()).projectId();
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());
        writeAssetBatchAudit(dryRun ? "PROTOTYPE_SYNC_DRY_RUN" : "PROTOTYPE_SYNC", "PAGE", projectId, "SUCCEEDED");
        List<AssetImportItemResponse> items = new ArrayList<>();
        for (int i = 0; i < request.pages().size(); i++) {
            items.add(syncPrototypePage(projectId, source, request, request.pages().get(i), i + 1, dryRun));
        }
        return new AssetPrototypeSyncResponse(
                source,
                dryRun,
                request.pages().size(),
                countAction(items, "CREATE"),
                countAction(items, "UPDATE"),
                countAction(items, "LINK_EXISTING"),
                (int) items.stream().filter(item -> "FAILED".equals(item.status())).count(),
                items
        );
    }

    public AssetImpactAnalysisResponse analyzeImpact(String projectId, String rawSubjectType, UUID subjectId) {
        String scopeProjectId = projectContext(projectId).projectId();
        String subjectType = subjectType(rawSubjectType);
        Map<UUID, AssetRequirement> requirements = mapById(repository.requirements(scopeProjectId), AssetRequirement::id);
        Map<UUID, AssetApi> apis = mapById(repository.apis(scopeProjectId), AssetApi::id);
        Map<UUID, AssetPage> pages = mapById(repository.pages(scopeProjectId), AssetPage::id);
        Map<UUID, AssetBusinessFlow> flows = mapById(repository.businessFlows(scopeProjectId), AssetBusinessFlow::id);
        Map<UUID, TestCaseRecord> cases = mapById(repository.testCases(scopeProjectId), TestCaseRecord::id);
        List<TraceLink> links = repository.traceLinks(null, null, null, null, null);
        LinkedHashSet<UUID> requirementIds = new LinkedHashSet<>();
        LinkedHashSet<UUID> apiIds = new LinkedHashSet<>();
        LinkedHashSet<UUID> pageIds = new LinkedHashSet<>();
        LinkedHashSet<UUID> flowIds = new LinkedHashSet<>();
        LinkedHashSet<UUID> caseIds = new LinkedHashSet<>();

        if (subjectType == null || subjectId == null) {
            requirementIds.addAll(requirements.keySet());
            apiIds.addAll(apis.keySet());
            pageIds.addAll(pages.keySet());
            flowIds.addAll(flows.keySet());
            caseIds.addAll(cases.keySet());
        } else {
            addSubject(subjectType, subjectId, requirements, apis, pages, flows, cases,
                    requirementIds, apiIds, pageIds, flowIds, caseIds);
        }

        boolean changed;
        do {
            changed = false;
            for (TraceLink link : links) {
                if (link.requirementId() == null || !requirements.containsKey(link.requirementId())) {
                    continue;
                }
                boolean related = requirementIds.contains(link.requirementId())
                        || (link.apiId() != null && apiIds.contains(link.apiId()))
                        || (link.pageId() != null && pageIds.contains(link.pageId()))
                        || (link.flowId() != null && flowIds.contains(link.flowId()))
                        || (link.caseId() != null && caseIds.contains(link.caseId()));
                if (!related) {
                    continue;
                }
                changed |= requirementIds.add(link.requirementId());
                if (link.apiId() != null && apis.containsKey(link.apiId())) {
                    changed |= apiIds.add(link.apiId());
                }
                if (link.pageId() != null && pages.containsKey(link.pageId())) {
                    changed |= pageIds.add(link.pageId());
                }
                if (link.flowId() != null && flows.containsKey(link.flowId())) {
                    changed |= flowIds.add(link.flowId());
                }
                if (link.caseId() != null && cases.containsKey(link.caseId())) {
                    changed |= caseIds.add(link.caseId());
                }
            }
        } while (changed);

        List<String> gaps = impactGaps(requirementIds, apiIds, pageIds, flowIds, caseIds, links, requirements, apis, pages, flows, cases);
        List<AssetImpactNodeResponse> requirementNodes = nodes(requirementIds, requirements, AssetService::toImpactNode);
        List<AssetImpactNodeResponse> apiNodes = nodes(apiIds, apis, AssetService::toImpactNode);
        List<AssetImpactNodeResponse> pageNodes = nodes(pageIds, pages, AssetService::toImpactNode);
        List<AssetImpactNodeResponse> flowNodes = nodes(flowIds, flows, AssetService::toImpactNode);
        List<AssetImpactNodeResponse> caseNodes = nodes(caseIds, cases, AssetService::toImpactNode);
        writeAssetBatchAudit("IMPACT_ANALYSIS", "ASSET_IMPACT", scopeProjectId, "SUCCEEDED");
        return new AssetImpactAnalysisResponse(
                scopeProjectId,
                subjectType,
                subjectId,
                requirementNodes.size(),
                apiNodes.size(),
                pageNodes.size(),
                flowNodes.size(),
                caseNodes.size(),
                requirementNodes,
                apiNodes,
                pageNodes,
                flowNodes,
                caseNodes,
                gaps,
                Instant.now()
        );
    }

    private AssetImportItemResponse syncPrototypePage(
            String projectId,
            String source,
            AssetPrototypeSyncRequest request,
            AssetPrototypeSyncRequest.PageItem item,
            int row,
            boolean dryRun
    ) {
        try {
            String sourceRef = trimToNull(item.sourceRef());
            if (sourceRef == null) {
                return new AssetImportItemResponse(row, "INVALID", null, null, "FAILED", "sourceRef 不能为空", List.of("sourceRef 不能为空"));
            }
            Optional<AssetPage> existing = repository.pageBySourceRef(projectId, source, sourceRef);
            if (existing.isPresent()) {
                AssetPage merged = mergePrototypePage(existing.get(), source, request, item);
                if (samePage(existing.get(), merged)) {
                    return new AssetImportItemResponse(row, "LINK_EXISTING", existing.get().id(), existing.get().code(), dryRun ? "PLANNED" : "SUCCEEDED", "无差异，复用既有页面", List.of());
                }
                if (!dryRun) {
                    writeProjectAudit("PROTOTYPE_SYNC_UPDATE", "PAGE", existing.get().id(), projectId);
                    repository.savePage(merged);
                }
                return new AssetImportItemResponse(row, "UPDATE", existing.get().id(), existing.get().code(), dryRun ? "PLANNED" : "SUCCEEDED", "同步更新页面", List.of());
            }
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            AssetPage created = new AssetPage(
                    id,
                    assetCode("PAGE", id),
                    item.name(),
                    trimToNull(item.urlPattern()),
                    source,
                    sourceRef,
                    firstText(item.sourceVersion(), request.sourceVersion()),
                    jsonValue(item.componentTree()),
                    trimToNull(item.screenshotUrl()),
                    projectId,
                    initialStatus(item.status(), "ACTIVE", "PAGE"),
                    "ACTIVE",
                    null,
                    null,
                    now,
                    now
            );
            if (!dryRun) {
                writeProjectAudit("PROTOTYPE_SYNC_CREATE", "PAGE", id, projectId);
                repository.savePage(created);
            }
            return new AssetImportItemResponse(row, "CREATE", id, created.code(), dryRun ? "PLANNED" : "SUCCEEDED", "同步创建页面", List.of());
        } catch (BusinessException e) {
            return new AssetImportItemResponse(row, "FAILED", null, null, "FAILED", e.getMessage(), List.of(e.getMessage()));
        }
    }

    private AssetPage mergePrototypePage(
            AssetPage existing,
            String source,
            AssetPrototypeSyncRequest request,
            AssetPrototypeSyncRequest.PageItem item
    ) {
        return new AssetPage(
                existing.id(),
                existing.code(),
                item.name(),
                trimToNull(item.urlPattern()),
                source,
                trimToNull(item.sourceRef()),
                firstText(item.sourceVersion(), request.sourceVersion(), existing.sourceVersion()),
                jsonValue(item.componentTree()),
                trimToNull(item.screenshotUrl()),
                existing.projectId(),
                initialStatus(item.status(), existing.status(), "PAGE"),
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                Instant.now()
        );
    }

    private boolean samePage(AssetPage left, AssetPage right) {
        return Objects.equals(left.name(), right.name())
                && Objects.equals(left.urlPattern(), right.urlPattern())
                && Objects.equals(left.source(), right.source())
                && Objects.equals(left.sourceRef(), right.sourceRef())
                && Objects.equals(left.sourceVersion(), right.sourceVersion())
                && Objects.equals(jsonNode(left.componentTree()), jsonNode(right.componentTree()))
                && Objects.equals(left.screenshotUrl(), right.screenshotUrl())
                && Objects.equals(left.status(), right.status());
    }

    private static String firstText(String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static <T> Map<UUID, T> mapById(List<T> items, Function<T, UUID> idGetter) {
        Map<UUID, T> result = new LinkedHashMap<>();
        for (T item : items) {
            UUID id = idGetter.apply(item);
            if (id != null) {
                result.put(id, item);
            }
        }
        return result;
    }

    private static String subjectType(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String value = rawValue.trim().toUpperCase(Locale.ROOT);
        String canonical = IMPACT_SUBJECT_TYPE_ALIASES.getOrDefault(value, value);
        if (!IMPACT_SUBJECT_TYPES.contains(canonical)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "assetType 不合法: " + rawValue);
        }
        return canonical;
    }

    private static void addSubject(
            String subjectType,
            UUID subjectId,
            Map<UUID, AssetRequirement> requirements,
            Map<UUID, AssetApi> apis,
            Map<UUID, AssetPage> pages,
            Map<UUID, AssetBusinessFlow> flows,
            Map<UUID, TestCaseRecord> cases,
            LinkedHashSet<UUID> requirementIds,
            LinkedHashSet<UUID> apiIds,
            LinkedHashSet<UUID> pageIds,
            LinkedHashSet<UUID> flowIds,
            LinkedHashSet<UUID> caseIds
    ) {
        boolean exists = switch (subjectType) {
            case "REQUIREMENT" -> requirementIds.add(subjectId) && requirements.containsKey(subjectId);
            case "API" -> apiIds.add(subjectId) && apis.containsKey(subjectId);
            case "PAGE" -> pageIds.add(subjectId) && pages.containsKey(subjectId);
            case "FLOW" -> flowIds.add(subjectId) && flows.containsKey(subjectId);
            case "CASE" -> caseIds.add(subjectId) && cases.containsKey(subjectId);
            default -> false;
        };
        if (!exists) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "影响分析资产不存在: " + subjectType + "/" + subjectId);
        }
    }

    private static List<String> impactGaps(
            Set<UUID> requirementIds,
            Set<UUID> apiIds,
            Set<UUID> pageIds,
            Set<UUID> flowIds,
            Set<UUID> caseIds,
            List<TraceLink> links,
            Map<UUID, AssetRequirement> requirements,
            Map<UUID, AssetApi> apis,
            Map<UUID, AssetPage> pages,
            Map<UUID, AssetBusinessFlow> flows,
            Map<UUID, TestCaseRecord> cases
    ) {
        List<String> gaps = new ArrayList<>();
        for (UUID requirementId : requirementIds) {
            AssetRequirement requirement = requirements.get(requirementId);
            if (requirement == null) {
                continue;
            }
            boolean hasApi = links.stream().anyMatch(link -> requirementId.equals(link.requirementId())
                    && link.apiId() != null && apis.containsKey(link.apiId()));
            boolean hasPage = links.stream().anyMatch(link -> requirementId.equals(link.requirementId())
                    && link.pageId() != null && pages.containsKey(link.pageId()));
            boolean hasFlow = links.stream().anyMatch(link -> requirementId.equals(link.requirementId())
                    && link.flowId() != null && flows.containsKey(link.flowId()));
            boolean hasCase = links.stream().anyMatch(link -> requirementId.equals(link.requirementId())
                    && link.caseId() != null && cases.containsKey(link.caseId()));
            if (!hasApi) {
                gaps.add("需求 " + requirement.code() + " 缺少 API 覆盖");
            }
            if (!hasPage) {
                gaps.add("需求 " + requirement.code() + " 缺少页面覆盖");
            }
            if (!hasFlow) {
                gaps.add("需求 " + requirement.code() + " 缺少业务流覆盖");
            }
            if (!hasCase) {
                gaps.add("需求 " + requirement.code() + " 缺少测试用例覆盖");
            }
        }
        for (UUID apiId : apiIds) {
            boolean linked = links.stream().anyMatch(link -> apiId.equals(link.apiId()));
            if (!linked && apis.containsKey(apiId)) {
                gaps.add("API " + apis.get(apiId).code() + " 未关联需求");
            }
        }
        for (UUID pageId : pageIds) {
            boolean linked = links.stream().anyMatch(link -> pageId.equals(link.pageId()));
            if (!linked && pages.containsKey(pageId)) {
                gaps.add("页面 " + pages.get(pageId).code() + " 未关联需求");
            }
        }
        for (UUID flowId : flowIds) {
            boolean linked = links.stream().anyMatch(link -> flowId.equals(link.flowId()));
            if (!linked && flows.containsKey(flowId)) {
                gaps.add("业务流 " + flows.get(flowId).code() + " 未关联需求");
            }
        }
        for (UUID caseId : caseIds) {
            boolean linked = links.stream().anyMatch(link -> caseId.equals(link.caseId()));
            if (!linked && cases.containsKey(caseId)) {
                gaps.add("测试用例 " + cases.get(caseId).code() + " 未关联需求");
            }
        }
        return gaps;
    }

    private static <T> List<AssetImpactNodeResponse> nodes(
            Set<UUID> ids,
            Map<UUID, T> source,
            Function<T, AssetImpactNodeResponse> mapper
    ) {
        return ids.stream()
                .map(source::get)
                .filter(Objects::nonNull)
                .map(mapper)
                .sorted(Comparator.comparing(AssetImpactNodeResponse::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private static AssetImpactNodeResponse toImpactNode(AssetRequirement value) {
        return new AssetImpactNodeResponse(
                "REQUIREMENT",
                value.id(),
                value.code(),
                value.title(),
                value.projectId(),
                value.status(),
                lifecycleStatus(value.lifecycleStatus(), value.deletedAt()),
                value.updatedAt()
        );
    }

    private static AssetImpactNodeResponse toImpactNode(AssetApi value) {
        return new AssetImpactNodeResponse(
                "API",
                value.id(),
                value.code(),
                value.summary(),
                value.projectId(),
                value.status(),
                lifecycleStatus(value.lifecycleStatus(), value.deletedAt()),
                value.updatedAt()
        );
    }

    private static AssetImpactNodeResponse toImpactNode(AssetPage value) {
        return new AssetImpactNodeResponse(
                "PAGE",
                value.id(),
                value.code(),
                value.name(),
                value.projectId(),
                value.status(),
                lifecycleStatus(value.lifecycleStatus(), value.deletedAt()),
                value.updatedAt()
        );
    }

    private static AssetImpactNodeResponse toImpactNode(AssetBusinessFlow value) {
        return new AssetImpactNodeResponse(
                "FLOW",
                value.id(),
                value.code(),
                value.name(),
                value.projectId(),
                value.status(),
                lifecycleStatus(value.lifecycleStatus(), value.deletedAt()),
                value.updatedAt()
        );
    }

    private static AssetImpactNodeResponse toImpactNode(TestCaseRecord value) {
        return new AssetImpactNodeResponse(
                "CASE",
                value.id(),
                value.code(),
                value.title(),
                value.projectId(),
                value.status(),
                lifecycleStatus(value.lifecycleStatus(), value.deletedAt()),
                value.updatedAt()
        );
    }

    private List<Map<String, String>> parseImportRows(String assetType, String format, String content) {
        if ("CSV".equals(format)) {
            return parseCsv(content);
        }
        if ("OPENAPI".equals(format)) {
            return parseOpenApi(content);
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode rows = root.isArray() ? root : root.path("items");
            if (!rows.isArray()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JSON 导入内容必须是数组或包含 items 数组");
            }
            List<Map<String, String>> result = new ArrayList<>();
            for (JsonNode item : rows) {
                result.add(flattenJsonObject(item));
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JSON 导入内容格式不合法");
        }
    }

    private List<Map<String, String>> parseOpenApi(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            String apiVersion = trimToNull(textOrNull(root.path("info").path("version")));
            JsonNode paths = root.path("paths");
            if (!paths.isObject()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "OpenAPI 内容缺少 paths");
            }
            List<Map<String, String>> rows = new ArrayList<>();
            paths.fields().forEachRemaining(pathEntry -> pathEntry.getValue().fields().forEachRemaining(methodEntry -> {
                String method = methodEntry.getKey().toUpperCase(Locale.ROOT);
                if (!API_HTTP_METHODS.contains(method)) {
                    return;
                }
                JsonNode operation = methodEntry.getValue();
                Map<String, String> row = new LinkedHashMap<>();
                row.put("path", pathEntry.getKey());
                row.put("httpMethod", method);
                row.put("summary", textOrDefault(operation.path("summary"), method + " " + pathEntry.getKey()));
                row.put("description", textOrNull(operation.path("description")));
                row.put("status", "ACTIVE");
                row.put("source", "OPENAPI");
                row.put("sourceRef", openApiSourceRef(pathEntry.getKey(), method));
                row.put("version", apiVersion);
                row.put("requestSchema", openApiRequestSchema(operation));
                row.put("responseSchema", openApiResponseSchema(operation));
                rows.add(row);
            }));
            return rows;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "OpenAPI 导入内容格式不合法");
        }
    }

    private ImportPlan planImportRow(String assetType, String projectId, Map<String, String> row, int rowNumber) {
        List<String> errors = validateImportRow(assetType, row);
        if (!errors.isEmpty()) {
            return ImportPlan.failed(rowNumber, "INVALID", "校验失败", errors);
        }
        return switch (assetType) {
            case "REQUIREMENT" -> planRequirementImport(projectId, rowNumber, row);
            case "API" -> planApiImport(projectId, rowNumber, row);
            case "TEST_CASE" -> planTestCaseImport(projectId, rowNumber, row);
            default -> ImportPlan.failed(rowNumber, "INVALID", "assetType 不合法", List.of("assetType 不合法"));
        };
    }

    private ImportPlan planRequirementImport(String projectId, int rowNumber, Map<String, String> row) {
        String sourceRef = trimToNull(rowValue(row, "sourceRef"));
        if (sourceRef != null) {
            Optional<AssetRequirement> existing = repository.requirementBySourceRef(projectId, "IMPORT", sourceRef);
            if (existing.isPresent()) {
                AssetRequirement merged = mergeImportedRequirement(existing.get(), requirementImportRequest(projectId, row), Instant.now());
                if (sameRequirement(existing.get(), merged)) {
                    return ImportPlan.planned(rowNumber, "LINK_EXISTING", existing.get().id(), existing.get().code(), "无差异，复用既有需求");
                }
                if (!"DRAFT".equals(existing.get().status())) {
                    return ImportPlan.failed(
                            rowNumber,
                            "CONFLICT_REVIEW_REQUIRED",
                            "既有导入需求已进入评审或审批状态，需人工处理差异后再更新",
                            List.of("status=" + existing.get().status())
                    );
                }
                return ImportPlan.planned(rowNumber, "UPDATE", existing.get().id(), existing.get().code(), "将更新既有 DRAFT 需求");
            }
        }
        return ImportPlan.planned(rowNumber, "CREATE", null, null, "将创建需求");
    }

    private ImportPlan planApiImport(String projectId, int rowNumber, Map<String, String> row) {
        String path = trimToNull(rowValue(row, "path"));
        String httpMethod = trimToNull(rowValue(row, "httpMethod"));
        Optional<AssetApi> existing = repository.apiByPath(projectId, path, httpMethod);
        if (existing.isPresent()) {
            if (!"OPENAPI".equals(apiImportSource(row))) {
                return ImportPlan.failed(
                        rowNumber,
                        "CONFLICT_REVIEW_REQUIRED",
                        "同项目下 API 路径和方法已存在，需人工处理",
                        List.of("path/httpMethod 已存在")
                );
            }
            AssetApi merged = mergeImportedApi(existing.get(), row, Instant.now());
            if (sameApi(existing.get(), merged)) {
                return ImportPlan.planned(rowNumber, "LINK_EXISTING", existing.get().id(), existing.get().code(), "无差异，复用既有 API");
            }
            return ImportPlan.planned(rowNumber, "UPDATE", existing.get().id(), existing.get().code(), "将更新既有 API 资产");
        }
        return ImportPlan.planned(rowNumber, "CREATE", null, null, "将创建 API 资产");
    }

    private AssetApi createImportedApi(String projectId, Map<String, String> row) {
        String scopeId = projectContext(projectId).projectId();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AssetApi api = new AssetApi(
                id,
                assetCode("API", id),
                rowValue(row, "summary"),
                trimToNull(rowValue(row, "description")),
                valueIn(rowValue(row, "httpMethod"), null, API_HTTP_METHODS, "httpMethod"),
                rowValue(row, "path"),
                apiImportSource(row),
                trimToNull(rowValue(row, "sourceRef")),
                trimToNull(rowValue(row, "version")),
                defaultJson(rowValue(row, "requestSchema")),
                defaultJson(rowValue(row, "responseSchema")),
                projectId,
                initialStatus(rowValue(row, "status"), "ACTIVE", "API"),
                "ACTIVE",
                null,
                null,
                now,
                now
        );
        writeProjectAudit("CREATE", "API", id, scopeId);
        AssetApi stored = repository.saveApi(api);
        log.info("Created imported api id={}, path={}, method={}, trace_id={}",
                id, api.path(), api.httpMethod(), TraceContext.getTraceId());
        return stored;
    }

    private AssetApi updateImportedApi(UUID id, Map<String, String> row) {
        AssetApi existing = repository.api(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + id));
        AssetApi merged = mergeImportedApi(existing, row, Instant.now());
        writeProjectAudit("UPDATE", "API", id, existing.projectId());
        AssetApi stored = repository.saveApi(merged);
        log.info("Updated imported api id={}, path={}, method={}, trace_id={}",
                id, stored.path(), stored.httpMethod(), TraceContext.getTraceId());
        return stored;
    }

    private AssetApi mergeImportedApi(AssetApi existing, Map<String, String> row, Instant now) {
        return new AssetApi(
                existing.id(),
                existing.code(),
                rowValue(row, "summary"),
                trimToNull(rowValue(row, "description")),
                valueIn(rowValue(row, "httpMethod"), null, API_HTTP_METHODS, "httpMethod"),
                rowValue(row, "path"),
                apiImportSource(row),
                trimToNull(rowValue(row, "sourceRef")),
                trimToNull(rowValue(row, "version")),
                defaultJson(rowValue(row, "requestSchema")),
                defaultJson(rowValue(row, "responseSchema")),
                existing.projectId(),
                initialStatus(rowValue(row, "status"), existing.status(), "API"),
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
    }

    private boolean sameApi(AssetApi left, AssetApi right) {
        return Objects.equals(left.summary(), right.summary())
                && Objects.equals(left.description(), right.description())
                && Objects.equals(left.httpMethod(), right.httpMethod())
                && Objects.equals(left.path(), right.path())
                && Objects.equals(left.source(), right.source())
                && Objects.equals(left.sourceRef(), right.sourceRef())
                && Objects.equals(left.version(), right.version())
                && Objects.equals(jsonNode(left.requestSchema()), jsonNode(right.requestSchema()))
                && Objects.equals(jsonNode(left.responseSchema()), jsonNode(right.responseSchema()))
                && Objects.equals(left.status(), right.status());
    }

    private static String apiImportSource(Map<String, String> row) {
        return valueIn(rowValue(row, "source"), "IMPORT", API_SOURCES, "source");
    }

    private ImportPlan planTestCaseImport(String projectId, int rowNumber, Map<String, String> row) {
        UUID requirementId = uuidOrNull(rowValue(row, "requirementId"));
        UUID apiId = uuidOrNull(rowValue(row, "apiId"));
        try {
            validateRequirementBelongsToProject(requirementId, projectId);
            validateApiBelongsToProject(apiId, projectId);
        } catch (BusinessException e) {
            return ImportPlan.failed(rowNumber, "INVALID", "关联资产校验失败", List.of(e.getMessage()));
        }
        return ImportPlan.planned(rowNumber, "CREATE", null, null, "将创建测试用例");
    }

    private AssetImportItemResponse applyImportPlan(
            String assetType,
            String projectId,
            Map<String, String> row,
            ImportPlan plan
    ) {
        try {
            return switch (assetType) {
                case "REQUIREMENT" -> importRequirement(projectId, row, plan.row());
                case "API" -> importApi(projectId, row, plan.row());
                case "TEST_CASE" -> importTestCase(projectId, row, plan.row());
                default -> plan.toResponse();
            };
        } catch (BusinessException e) {
            return new AssetImportItemResponse(plan.row(), "FAILED", null, null, "FAILED", e.getMessage(), List.of(e.getMessage()));
        }
    }

    private AssetImportItemResponse importRequirement(String projectId, Map<String, String> row, int rowNumber) {
        ImportPlan before = planRequirementImport(projectId, rowNumber, row);
        if (!"PLANNED".equals(before.status())) {
            return before.toResponse();
        }
        RequirementResponse saved = createRequirement(requirementImportRequest(projectId, row));
        ImportPlan after = planRequirementImport(projectId, rowNumber, row);
        String action = "LINK_EXISTING".equals(after.action()) ? before.action() : after.action();
        if ("LINK_EXISTING".equals(action) && "CREATE".equals(before.action())) {
            action = "CREATE";
        }
        return new AssetImportItemResponse(rowNumber, action, saved.id(), saved.code(), "SUCCEEDED", "导入成功", List.of());
    }

    private AssetImportItemResponse importApi(String projectId, Map<String, String> row, int rowNumber) {
        ImportPlan before = planApiImport(projectId, rowNumber, row);
        if (!"PLANNED".equals(before.status())) {
            return before.toResponse();
        }
        if ("LINK_EXISTING".equals(before.action())) {
            return new AssetImportItemResponse(
                    rowNumber,
                    "LINK_EXISTING",
                    before.id(),
                    before.code(),
                    "SUCCEEDED",
                    "导入成功",
                    List.of()
            );
        }
        AssetApi saved = switch (before.action()) {
            case "UPDATE" -> updateImportedApi(before.id(), row);
            default -> createImportedApi(projectId, row);
        };
        return new AssetImportItemResponse(rowNumber, before.action(), saved.id(), saved.code(), "SUCCEEDED", "导入成功", List.of());
    }

    private AssetImportItemResponse importTestCase(String projectId, Map<String, String> row, int rowNumber) {
        TestCaseResponse saved = createTestCase(new CreateTestCaseRequest(
                rowValue(row, "title"),
                rowValue(row, "description"),
                uuidOrNull(rowValue(row, "requirementId")),
                uuidOrNull(rowValue(row, "apiId")),
                projectId,
                rowValue(row, "status"),
                rowValue(row, "priority"),
                rowValue(row, "tags"),
                parseImportSteps(rowValue(row, "steps"))
        ));
        return new AssetImportItemResponse(rowNumber, "CREATE", saved.id(), saved.code(), "SUCCEEDED", "导入成功", List.of());
    }

    private static AssetImportResponse toImportResponse(
            String assetType,
            String format,
            boolean dryRun,
            int totalRows,
            List<AssetImportItemResponse> items
    ) {
        int created = countAction(items, "CREATE");
        int updated = countAction(items, "UPDATE");
        int skipped = (int) items.stream().filter(item -> "LINK_EXISTING".equals(item.action())).count();
        int failed = (int) items.stream().filter(item -> "FAILED".equals(item.status())).count();
        return new AssetImportResponse(assetType, format, dryRun, totalRows, created, updated, skipped, failed, items);
    }

    private static int countAction(List<AssetImportItemResponse> items, String action) {
        return (int) items.stream()
                .filter(item -> action.equals(item.action()))
                .filter(item -> !"FAILED".equals(item.status()))
                .count();
    }

    private List<String> validateImportRow(String assetType, Map<String, String> row) {
        List<String> errors = new ArrayList<>();
        switch (assetType) {
            case "REQUIREMENT" -> {
                requireImportField(row, "title", errors);
                validateImportEnum(row, "status", REVIEW_STATUSES, errors);
                validateImportEnum(row, "priority", PRIORITIES, errors);
            }
            case "API" -> {
                requireImportField(row, "summary", errors);
                requireImportField(row, "httpMethod", errors);
                requireImportField(row, "path", errors);
                validateImportEnum(row, "source", API_SOURCES, errors);
                validateImportEnum(row, "status", API_STATUSES, errors);
                validateImportEnum(row, "httpMethod", API_HTTP_METHODS, errors);
            }
            case "TEST_CASE" -> {
                requireImportField(row, "title", errors);
                validateImportEnum(row, "status", REVIEW_STATUSES, errors);
                validateImportEnum(row, "priority", PRIORITIES, errors);
                validateUuidField(row, "requirementId", errors);
                validateUuidField(row, "apiId", errors);
            }
            default -> errors.add("assetType 不合法");
        }
        return errors;
    }

    private CreateRequirementRequest requirementImportRequest(String projectId, Map<String, String> row) {
        return new CreateRequirementRequest(
                rowValue(row, "title"),
                rowValue(row, "description"),
                rowValue(row, "status"),
                rowValue(row, "priority"),
                projectId,
                rowValue(row, "tags"),
                "IMPORT",
                rowValue(row, "sourceRef"),
                rowValue(row, "sourceUrl"),
                rowValue(row, "acceptanceCriteria")
        );
    }

    private String exportRequirements(AssetExportRequest request, String format) {
        List<RequirementResponse> rows = exportRequirementRows(request);
        if ("JSON".equals(format)) {
            return jsonString(rows.stream().map(AssetService::requirementExportMap).toList());
        }
        StringBuilder csv = new StringBuilder("code,title,description,status,priority,projectId,source,sourceRef,sourceUrl,acceptanceCriteria,tags,lifecycleStatus,createdAt,updatedAt\n");
        rows.forEach(row -> appendCsvLine(csv,
                row.code(), row.title(), row.description(), row.status(), row.priority(), row.projectId(),
                row.source(), row.sourceRef(), row.sourceUrl(), row.acceptanceCriteria(), row.tags(),
                row.lifecycleStatus(), row.createdAt(), row.updatedAt()));
        return csv.toString();
    }

    private String exportApis(AssetExportRequest request, String format) {
        List<ApiResponseDTO> rows = exportApiRows(request);
        if ("OPENAPI".equals(format)) {
            return exportApisOpenApi(rows);
        }
        if ("JSON".equals(format)) {
            return jsonString(rows.stream().map(AssetService::apiExportMap).toList());
        }
        StringBuilder csv = new StringBuilder("code,summary,description,httpMethod,path,status,projectId,source,sourceRef,version,requestSchema,responseSchema,lifecycleStatus,createdAt,updatedAt\n");
        rows.forEach(row -> appendCsvLine(csv,
                row.code(), row.summary(), row.description(), row.httpMethod(), row.path(), row.status(),
                row.projectId(), row.source(), row.sourceRef(), row.version(), row.requestSchema(), row.responseSchema(),
                row.lifecycleStatus(), row.createdAt(), row.updatedAt()));
        return csv.toString();
    }

    private String exportTestCases(AssetExportRequest request, String format) {
        List<TestCaseResponse> rows = exportTestCaseRows(request);
        if ("JSON".equals(format)) {
            return jsonString(rows.stream().map(AssetService::testCaseExportMap).toList());
        }
        StringBuilder csv = new StringBuilder("code,title,description,status,priority,projectId,requirementId,apiId,tags,steps,lifecycleStatus,createdAt,updatedAt\n");
        rows.forEach(row -> appendCsvLine(csv,
                row.code(), row.title(), row.description(), row.status(), row.priority(), row.projectId(),
                row.requirementId(), row.apiId(), row.tags(), jsonString(row.steps()),
                row.lifecycleStatus(), row.createdAt(), row.updatedAt()));
        return csv.toString();
    }

    // ---- Health ----

    public String health() {
        return "UP";
    }

    // ---- Mappers ----

    private static RequirementResponse toRequirementResponse(AssetRequirement r) {
        return new RequirementResponse(
                r.id(), r.code(), r.title(), r.description(), r.source(), r.sourceRef(), r.sourceUrl(), r.acceptanceCriteria(),
                r.status(), r.priority(),
                r.projectId(), r.tags(), r.version(),
                lifecycleStatus(r.lifecycleStatus(), r.deletedAt()), r.archivedAt(), r.deletedAt(),
                r.createdAt(), r.updatedAt()
        );
    }

    private static ApiResponseDTO toApiResponse(AssetApi a) {
        return new ApiResponseDTO(
                a.id(), a.code(), a.summary(), a.description(), a.httpMethod(), a.path(), a.source(), a.sourceRef(),
                a.version(),
                a.requestSchema(), a.responseSchema(), a.projectId(),
                a.status(),
                lifecycleStatus(a.lifecycleStatus(), a.deletedAt()), a.archivedAt(), a.deletedAt(),
                a.createdAt(), a.updatedAt()
        );
    }

    private static PageResponse toPageResponse(AssetPage p) {
        return new PageResponse(
                p.id(), p.code(), p.name(), p.urlPattern(), p.source(), p.sourceRef(), p.sourceVersion(), p.componentTree(), p.screenshotUrl(),
                p.projectId(), p.status(),
                lifecycleStatus(p.lifecycleStatus(), p.deletedAt()), p.archivedAt(), p.deletedAt(),
                p.createdAt(), p.updatedAt()
        );
    }

    private static BusinessFlowResponse toBusinessFlowResponse(AssetBusinessFlow f) {
        return new BusinessFlowResponse(
                f.id(), f.code(), f.name(), f.description(), f.flowJson(), f.priority(),
                f.projectId(), f.status(),
                lifecycleStatus(f.lifecycleStatus(), f.deletedAt()), f.archivedAt(), f.deletedAt(),
                f.createdAt(), f.updatedAt()
        );
    }

    private static TestCaseResponse toTestCaseResponse(TestCaseRecord tc, List<TestCaseStep> steps) {
        List<TestCaseStepResponse> stepResponses = steps == null ? Collections.emptyList()
                : steps.stream()
                        .sorted(Comparator.comparingInt(TestCaseStep::stepOrder))
                        .map(s -> new TestCaseStepResponse(s.stepOrder(), s.action(), s.expectedResult()))
                        .toList();
        return new TestCaseResponse(
                tc.id(), tc.code(), tc.title(), tc.description(), tc.requirementId(), tc.apiId(),
                tc.source(), tc.sourceRef(), tc.projectId(),
                tc.status(), tc.priority(), tc.tags(), stepResponses,
                tc.version(),
                lifecycleStatus(tc.lifecycleStatus(), tc.deletedAt()), tc.archivedAt(), tc.deletedAt(),
                tc.createdAt(), tc.updatedAt()
        );
    }

    private AssetVersionHistoryResponse toVersionHistoryResponse(AssetVersionHistory history) {
        return new AssetVersionHistoryResponse(
                history.id(),
                history.assetType(),
                history.assetId(),
                history.projectId(),
                history.version(),
                history.changeType(),
                history.actor(),
                splitChangedFields(history.changedFields()),
                jsonNode(history.diffJson()),
                jsonNode(history.snapshotJson()),
                history.traceId(),
                history.createdAt()
        );
    }

    private static TraceLinkResponse toTraceLinkResponse(TraceLink l) {
        return new TraceLinkResponse(l.id(), l.requirementId(), l.apiId(), l.pageId(), l.flowId(), l.caseId(), l.createdAt());
    }

    private void saveRequirementHistory(AssetRequirement requirement, String changeType, VersionDiff diff) {
        repository.saveVersionHistory(new AssetVersionHistory(
                UUID.randomUUID(),
                "REQUIREMENT",
                requirement.id(),
                requirement.projectId(),
                requirement.version(),
                changeType,
                currentActor(),
                String.join(",", diff.changedFields()),
                diff.diffJson(),
                jsonString(requirementSnapshot(requirement)),
                TraceContext.getTraceId(),
                Instant.now()
        ));
    }

    private void saveTestCaseHistory(TestCaseRecord testCase, String changeType, VersionDiff diff) {
        repository.saveVersionHistory(new AssetVersionHistory(
                UUID.randomUUID(),
                "TEST_CASE",
                testCase.id(),
                testCase.projectId(),
                testCase.version(),
                changeType,
                currentActor(),
                String.join(",", diff.changedFields()),
                diff.diffJson(),
                jsonString(testCaseSnapshot(testCase)),
                TraceContext.getTraceId(),
                Instant.now()
        ));
    }

    private VersionDiff requirementDiff(AssetRequirement before, AssetRequirement after) {
        LinkedHashMap<String, Object> diff = new LinkedHashMap<>();
        addDiff(diff, "title", before.title(), after.title());
        addDiff(diff, "description", before.description(), after.description());
        addDiff(diff, "sourceUrl", before.sourceUrl(), after.sourceUrl());
        addDiff(diff, "acceptanceCriteria", before.acceptanceCriteria(), after.acceptanceCriteria());
        addDiff(diff, "status", before.status(), after.status());
        addDiff(diff, "priority", before.priority(), after.priority());
        addDiff(diff, "tags", normalizedTags(before.tags()), normalizedTags(after.tags()));
        addDiff(diff, "lifecycleStatus", lifecycleStatus(before.lifecycleStatus(), before.deletedAt()),
                lifecycleStatus(after.lifecycleStatus(), after.deletedAt()));
        addDiff(diff, "archivedAt", before.archivedAt(), after.archivedAt());
        addDiff(diff, "deletedAt", before.deletedAt(), after.deletedAt());
        return versionDiff(diff);
    }

    private VersionDiff testCaseDiff(TestCaseRecord before, TestCaseRecord after) {
        LinkedHashMap<String, Object> diff = new LinkedHashMap<>();
        addDiff(diff, "title", before.title(), after.title());
        addDiff(diff, "description", before.description(), after.description());
        addDiff(diff, "requirementId", before.requirementId(), after.requirementId());
        addDiff(diff, "apiId", before.apiId(), after.apiId());
        addDiff(diff, "status", before.status(), after.status());
        addDiff(diff, "priority", before.priority(), after.priority());
        addDiff(diff, "tags", normalizedTags(before.tags()), normalizedTags(after.tags()));
        addDiff(diff, "steps", stepSnapshot(before.steps()), stepSnapshot(after.steps()));
        addDiff(diff, "lifecycleStatus", lifecycleStatus(before.lifecycleStatus(), before.deletedAt()),
                lifecycleStatus(after.lifecycleStatus(), after.deletedAt()));
        addDiff(diff, "archivedAt", before.archivedAt(), after.archivedAt());
        addDiff(diff, "deletedAt", before.deletedAt(), after.deletedAt());
        return versionDiff(diff);
    }

    private static void addDiff(LinkedHashMap<String, Object> diff, String field, Object before, Object after) {
        if (Objects.equals(before, after)) {
            return;
        }
        LinkedHashMap<String, Object> fieldDiff = new LinkedHashMap<>();
        fieldDiff.put("before", before);
        fieldDiff.put("after", after);
        diff.put(field, fieldDiff);
    }

    private static LinkedHashMap<String, Object> requirementSnapshot(AssetRequirement requirement) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", requirement.id());
        snapshot.put("code", requirement.code());
        snapshot.put("title", requirement.title());
        snapshot.put("description", requirement.description());
        snapshot.put("source", requirement.source());
        snapshot.put("sourceRef", requirement.sourceRef());
        snapshot.put("sourceUrl", requirement.sourceUrl());
        snapshot.put("acceptanceCriteria", requirement.acceptanceCriteria());
        snapshot.put("status", requirement.status());
        snapshot.put("priority", requirement.priority());
        snapshot.put("projectId", requirement.projectId());
        snapshot.put("tags", requirement.tags());
        snapshot.put("version", requirement.version());
        snapshot.put("lifecycleStatus", lifecycleStatus(requirement.lifecycleStatus(), requirement.deletedAt()));
        snapshot.put("archivedAt", requirement.archivedAt());
        snapshot.put("deletedAt", requirement.deletedAt());
        snapshot.put("createdAt", requirement.createdAt());
        snapshot.put("updatedAt", requirement.updatedAt());
        return snapshot;
    }

    private static LinkedHashMap<String, Object> testCaseSnapshot(TestCaseRecord testCase) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", testCase.id());
        snapshot.put("code", testCase.code());
        snapshot.put("title", testCase.title());
        snapshot.put("description", testCase.description());
        snapshot.put("projectId", testCase.projectId());
        snapshot.put("requirementId", testCase.requirementId());
        snapshot.put("apiId", testCase.apiId());
        snapshot.put("source", testCase.source());
        snapshot.put("sourceRef", testCase.sourceRef());
        snapshot.put("status", testCase.status());
        snapshot.put("priority", testCase.priority());
        snapshot.put("tags", testCase.tags());
        snapshot.put("steps", stepSnapshot(testCase.steps()));
        snapshot.put("version", testCase.version());
        snapshot.put("lifecycleStatus", lifecycleStatus(testCase.lifecycleStatus(), testCase.deletedAt()));
        snapshot.put("archivedAt", testCase.archivedAt());
        snapshot.put("deletedAt", testCase.deletedAt());
        snapshot.put("createdAt", testCase.createdAt());
        snapshot.put("updatedAt", testCase.updatedAt());
        return snapshot;
    }

    private static List<Map<String, Object>> stepSnapshot(List<TestCaseStep> steps) {
        if (steps == null) {
            return List.of();
        }
        return steps.stream()
                .sorted(Comparator.comparingInt(TestCaseStep::stepOrder))
                .map(step -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("order", step.stepOrder());
                    item.put("action", step.action());
                    item.put("expectedResult", step.expectedResult());
                    return item;
                })
                .toList();
    }

    private static String currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return "system";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof ServicePrincipal servicePrincipal) {
            return servicePrincipal.callerService() + ":" + servicePrincipal.delegatedUserId();
        }
        if (principal instanceof AuthUserPrincipal userPrincipal) {
            return StringUtils.hasText(userPrincipal.username())
                    ? userPrincipal.username()
                    : userPrincipal.userId().toString();
        }
        if (principal instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        return "system";
    }

    private static List<String> splitChangedFields(String changedFields) {
        if (!StringUtils.hasText(changedFields)) {
            return List.of();
        }
        return Arrays.stream(changedFields.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private JsonNode jsonNode(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }

    private AssetVersionHistory findVersionHistory(String assetType, UUID assetId, int version) {
        return repository.assetVersionHistory(assetType, assetId).stream()
                .filter(history -> history.version() == version)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "资产版本不存在: " + assetType + "/" + assetId + "/v" + version));
    }

    private static String snapshotText(JsonNode snapshot, String field) {
        JsonNode node = snapshot.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static String requiredSnapshotText(JsonNode snapshot, String field) {
        String value = snapshotText(snapshot, field);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "版本快照缺少字段: " + field);
        }
        return value;
    }

    private static Instant snapshotInstant(JsonNode snapshot, String field) {
        String value = snapshotText(snapshot, field);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "版本快照时间字段不合法: " + field);
        }
    }

    private static UUID snapshotUuid(JsonNode snapshot, String field) {
        String value = snapshotText(snapshot, field);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "版本快照 UUID 字段不合法: " + field);
        }
    }

    private static List<TestCaseStep> snapshotSteps(JsonNode stepsNode, UUID caseId) {
        if (!stepsNode.isArray()) {
            return List.of();
        }
        List<TestCaseStep> steps = new ArrayList<>();
        int order = 0;
        for (JsonNode item : stepsNode) {
            steps.add(new TestCaseStep(
                    UUID.randomUUID(),
                    caseId,
                    order++,
                    textOrDefault(item.path("action"), "待补充操作"),
                    textOrDefault(item.path("expectedResult"), "待补充预期")
            ));
        }
        return steps;
    }

    private String jsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "资产版本历史序列化失败");
        }
    }

    private VersionDiff versionDiff(LinkedHashMap<String, Object> diff) {
        if (diff.isEmpty()) {
            return VersionDiff.empty();
        }
        return new VersionDiff(new ArrayList<>(diff.keySet()), jsonString(diff));
    }

    private record VersionDiff(List<String> changedFields, String diffJson) {
        private static VersionDiff empty() {
            return new VersionDiff(List.of(), "{}");
        }
    }

    private void validateProjectWhenProvided(String projectId) {
        if (StringUtils.hasText(projectId)) {
            projectContext(projectId);
        }
    }

    private PlatformContextClient.ProjectContext projectContext(String projectId) {
        PlatformContextClient.ProjectContext context = contextClient.getProjectContext(projectId);
        if (!StringUtils.hasText(context.projectId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目上下文不存在: " + projectId);
        }
        if (!"ACTIVE".equals(context.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "项目状态不允许写入资产: " + projectId);
        }
        return context;
    }

    private void writeProjectAudit(String action, String resourceType, UUID resourceId, String projectId) {
        writeProjectAudit(action, resourceType, resourceId, projectId, "SUCCEEDED");
    }

    private void writeProjectAudit(String action, String resourceType, UUID resourceId, String projectId, String result) {
        String scopeId = StringUtils.hasText(projectId) ? projectContext(projectId).projectId() : null;
        contextClient.writeAuditEvent(action, resourceType, resourceId.toString(), scopeId, result);
    }

    private void writeAssetBatchAudit(String action, String resourceType, String projectId, String result) {
        String scopeId = StringUtils.hasText(projectId) ? projectContext(projectId).projectId() : null;
        contextClient.writeAuditEvent(action, resourceType, UUID.randomUUID().toString(), scopeId, result);
    }

    private List<RequirementResponse> exportRequirementRows(AssetExportRequest request) {
        request.setIndex(0);
        request.setSize(100);
        return listRequirements(request).items();
    }

    private List<ApiResponseDTO> exportApiRows(AssetExportRequest request) {
        request.setIndex(0);
        request.setSize(100);
        return listApis(request).items();
    }

    private List<TestCaseResponse> exportTestCaseRows(AssetExportRequest request) {
        request.setIndex(0);
        request.setSize(100);
        return listTestCases(request).items();
    }

    private static List<Map<String, String>> parseCsv(String content) {
        List<String> lines = content.lines()
                .filter(StringUtils::hasText)
                .toList();
        if (lines.isEmpty()) {
            return List.of();
        }
        List<String> headers = splitCsvLine(lines.getFirst()).stream()
                .map(String::trim)
                .toList();
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> values = splitCsvLine(lines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                row.put(headers.get(column), column < values.size() ? trimToNull(values.get(column)) : null);
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<String> splitCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values;
    }

    private Map<String, String> flattenJsonObject(JsonNode item) {
        if (!item.isObject()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JSON 导入数组元素必须是对象");
        }
        Map<String, String> row = new LinkedHashMap<>();
        item.fields().forEachRemaining(entry -> row.put(
                entry.getKey(),
                entry.getValue().isContainerNode() ? jsonString(entry.getValue()) : textOrNull(entry.getValue())
        ));
        return row;
    }

    private static void requireImportField(Map<String, String> row, String field, List<String> errors) {
        if (!StringUtils.hasText(rowValue(row, field))) {
            errors.add(field + " 不能为空");
        }
    }

    private static void validateImportEnum(
            Map<String, String> row,
            String field,
            Set<String> allowedValues,
            List<String> errors
    ) {
        String value = trimToNull(rowValue(row, field));
        if (value != null && !allowedValues.contains(value.toUpperCase(Locale.ROOT))) {
            errors.add(field + " 不合法: " + value);
        }
    }

    private static void validateUuidField(Map<String, String> row, String field, List<String> errors) {
        String value = trimToNull(rowValue(row, field));
        if (value == null) {
            return;
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            errors.add(field + " 不是合法 UUID");
        }
    }

    private static String rowValue(Map<String, String> row, String field) {
        if (row.containsKey(field)) {
            return row.get(field);
        }
        for (Map.Entry<String, String> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(field)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static UUID uuidOrNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : UUID.fromString(trimmed);
    }

    private static String defaultJson(String value) {
        return StringUtils.hasText(value) ? value : "{}";
    }

    private List<CreateTestCaseRequest.StepDto> parseImportSteps(String rawSteps) {
        if (!StringUtils.hasText(rawSteps)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(rawSteps);
            if (!root.isArray()) {
                return List.of(new CreateTestCaseRequest.StepDto(rawSteps, "待补充"));
            }
            List<CreateTestCaseRequest.StepDto> steps = new ArrayList<>();
            for (JsonNode item : root) {
                steps.add(new CreateTestCaseRequest.StepDto(
                        textOrDefault(item.path("action"), "待补充操作"),
                        textOrDefault(item.path("expectedResult"), "待补充预期")
                ));
            }
            return steps;
        } catch (JsonProcessingException e) {
            return List.of(new CreateTestCaseRequest.StepDto(rawSteps, "待补充"));
        }
    }

    private static Map<String, Object> requirementExportMap(RequirementResponse row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", row.code());
        item.put("title", row.title());
        item.put("description", row.description());
        item.put("status", row.status());
        item.put("priority", row.priority());
        item.put("projectId", row.projectId());
        item.put("source", row.source());
        item.put("sourceRef", row.sourceRef());
        item.put("sourceUrl", row.sourceUrl());
        item.put("acceptanceCriteria", row.acceptanceCriteria());
        item.put("tags", row.tags());
        item.put("lifecycleStatus", row.lifecycleStatus());
        item.put("createdAt", row.createdAt());
        item.put("updatedAt", row.updatedAt());
        return item;
    }

    private static Map<String, Object> apiExportMap(ApiResponseDTO row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", row.code());
        item.put("summary", row.summary());
        item.put("description", row.description());
        item.put("httpMethod", row.httpMethod());
        item.put("path", row.path());
        item.put("status", row.status());
        item.put("projectId", row.projectId());
        item.put("source", row.source());
        item.put("sourceRef", row.sourceRef());
        item.put("version", row.version());
        item.put("requestSchema", row.requestSchema());
        item.put("responseSchema", row.responseSchema());
        item.put("lifecycleStatus", row.lifecycleStatus());
        item.put("createdAt", row.createdAt());
        item.put("updatedAt", row.updatedAt());
        return item;
    }

    private static Map<String, Object> testCaseExportMap(TestCaseResponse row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", row.code());
        item.put("title", row.title());
        item.put("description", row.description());
        item.put("status", row.status());
        item.put("priority", row.priority());
        item.put("projectId", row.projectId());
        item.put("requirementId", row.requirementId());
        item.put("apiId", row.apiId());
        item.put("tags", row.tags());
        item.put("steps", row.steps());
        item.put("lifecycleStatus", row.lifecycleStatus());
        item.put("createdAt", row.createdAt());
        item.put("updatedAt", row.updatedAt());
        return item;
    }

    private String exportApisOpenApi(List<ApiResponseDTO> rows) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("openapi", "3.0.3");
        root.put("info", Map.of("title", "WP3 API Assets", "version", "1.0.0"));
        Map<String, Object> paths = new LinkedHashMap<>();
        for (ApiResponseDTO row : rows) {
            Map<String, Object> pathItem = castMap(paths.computeIfAbsent(row.path(), ignored -> new LinkedHashMap<>()));
            Map<String, Object> operation = new LinkedHashMap<>();
            operation.put("summary", row.summary());
            operation.put("description", row.description());
            operation.put("operationId", row.code());
            operation.put("x-wp3-lifecycleStatus", row.lifecycleStatus());
            operation.put("x-wp3-version", row.version());
            operation.put("x-wp3-source", row.source());
            operation.put("x-wp3-sourceRef", row.sourceRef());
            if (hasUsefulSchema(row.requestSchema())) {
                operation.put("requestBody", Map.of(
                        "required", false,
                        "content", Map.of("application/json", Map.of("schema", jsonNode(row.requestSchema())))
                ));
            }
            Map<String, Object> okResponse = new LinkedHashMap<>();
            okResponse.put("description", "OK");
            if (hasUsefulSchema(row.responseSchema())) {
                okResponse.put("content", Map.of("application/json", Map.of("schema", jsonNode(row.responseSchema()))));
            }
            operation.put("responses", Map.of("200", okResponse));
            pathItem.put(row.httpMethod().toLowerCase(Locale.ROOT), operation);
        }
        root.put("paths", paths);
        return jsonString(root);
    }

    private static String openApiSourceRef(String path, String method) {
        return "#/paths/" + openApiPointerSegment(path) + "/" + method.toLowerCase(Locale.ROOT);
    }

    private static String openApiPointerSegment(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private String openApiRequestSchema(JsonNode operation) {
        JsonNode schema = operation.path("requestBody").path("content").path("application/json").path("schema");
        return schema.isMissingNode() ? "{}" : jsonString(schema);
    }

    private String openApiResponseSchema(JsonNode operation) {
        JsonNode responses = operation.path("responses");
        if (!responses.isObject()) {
            return "{}";
        }
        for (String code : List.of("200", "201", "202", "default")) {
            String schema = openApiResponseSchemaByCode(responses.path(code));
            if (schema != null) {
                return schema;
            }
        }
        var fields = responses.fields();
        while (fields.hasNext()) {
            String schema = openApiResponseSchemaByCode(fields.next().getValue());
            if (schema != null) {
                return schema;
            }
        }
        return "{}";
    }

    private String openApiResponseSchemaByCode(JsonNode response) {
        JsonNode schema = response.path("content").path("application/json").path("schema");
        return schema.isMissingNode() ? null : jsonString(schema);
    }

    private static boolean hasUsefulSchema(String schema) {
        return StringUtils.hasText(schema) && !"{}".equals(schema.trim());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static void appendCsvLine(StringBuilder csv, Object... values) {
        for (Object value : values) {
            appendCsvValue(csv, value);
        }
        csv.setLength(csv.length() - 1);
        csv.append('\n');
    }

    private static void appendCsvValue(StringBuilder csv, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        csv.append('"').append(text.replace("\"", "\"\"")).append('"').append(',');
    }

    private static String importExportAssetType(String rawValue) {
        return valueIn(rawValue, null, IMPORT_EXPORT_ASSET_TYPES, "assetType");
    }

    private static String importExportFormat(String assetType, String rawValue, String operationName) {
        String format = valueIn(rawValue, FORMAT_CSV, IMPORT_EXPORT_FORMATS, "format");
        if (!IMPORT_EXPORT_FORMATS_BY_ASSET_TYPE.getOrDefault(assetType, Set.of()).contains(format)) {
            if (FORMAT_OPENAPI.equals(format)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "OpenAPI " + operationName + "仅支持 API 资产");
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "format 不支持当前 assetType: " + format + "/" + assetType);
        }
        return format;
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static String textOrDefault(JsonNode node, String defaultValue) {
        String value = textOrNull(node);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private record ImportPlan(
            int row,
            String action,
            UUID id,
            String code,
            String status,
            String message,
            List<String> errors
    ) {
        private static ImportPlan planned(int row, String action, UUID id, String code, String message) {
            return new ImportPlan(row, action, id, code, "PLANNED", message, List.of());
        }

        private static ImportPlan failed(int row, String action, String message, List<String> errors) {
            return new ImportPlan(row, action, null, null, "FAILED", message, errors);
        }

        private AssetImportItemResponse toResponse() {
            return new AssetImportItemResponse(row, action, id, code, status, message, errors);
        }
    }

    private void validateRequirementBelongsToProject(UUID requirementId, String projectId) {
        if (requirementId == null) {
            return;
        }
        AssetRequirement requirement = repository.requirement(requirementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + requirementId));
        ensureSameProject("需求", requirement.id(), requirement.projectId(), projectId);
    }

    private void validateApiBelongsToProject(UUID apiId, String projectId) {
        if (apiId == null) {
            return;
        }
        AssetApi api = repository.api(apiId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + apiId));
        ensureSameProject("API", api.id(), api.projectId(), projectId);
    }

    private void validatePageBelongsToProject(UUID pageId, String projectId) {
        if (pageId == null) {
            return;
        }
        AssetPage page = repository.page(pageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + pageId));
        ensureSameProject("页面", page.id(), page.projectId(), projectId);
    }

    private void validateBusinessFlowBelongsToProject(UUID flowId, String projectId) {
        if (flowId == null) {
            return;
        }
        AssetBusinessFlow flow = repository.businessFlow(flowId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流资产不存在: " + flowId));
        ensureSameProject("业务流", flow.id(), flow.projectId(), projectId);
    }

    private void validateTestCaseBelongsToProject(UUID caseId, String projectId) {
        if (caseId == null) {
            return;
        }
        TestCaseRecord testCase = repository.testCase(caseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + caseId));
        ensureSameProject("测试用例", testCase.id(), testCase.projectId(), projectId);
    }

    private void ensureSameProject(String resourceName, UUID resourceId, String actualProjectId, String expectedProjectId) {
        if (!StringUtils.hasText(actualProjectId) || !actualProjectId.equals(expectedProjectId)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    resourceName + "不属于当前项目: " + resourceId
            );
        }
    }

    private static String valueIn(String rawValue, String defaultValue, Set<String> allowedValues, String fieldName) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (!allowedValues.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不合法: " + rawValue);
        }
        return value;
    }

    private static String initialStatus(String rawValue, String defaultValue, String resourceType) {
        return switch (resourceType) {
            case "API" -> valueIn(rawValue, defaultValue, API_STATUSES, "status");
            case "PAGE" -> valueIn(rawValue, defaultValue, PAGE_STATUSES, "status");
            case "BUSINESS_FLOW" -> valueIn(rawValue, defaultValue, FLOW_STATUSES, "status");
            default -> valueIn(rawValue, defaultValue, REVIEW_STATUSES, "status");
        };
    }

    private String nextLifecycle(
            String resourceType,
            UUID resourceId,
            String projectId,
            String currentLifecycle,
            String rawNextLifecycle
    ) {
        String current = lifecycleStatus(currentLifecycle, null);
        String next = valueIn(rawNextLifecycle, current, LIFECYCLE_STATUSES, "lifecycleStatus");
        if (current.equals(next)) {
            return next;
        }
        boolean allowed = switch (current) {
            case "ACTIVE" -> "ARCHIVED".equals(next) || "DELETED".equals(next);
            case "ARCHIVED" -> "ACTIVE".equals(next) || "DELETED".equals(next);
            case "DELETED" -> "ACTIVE".equals(next);
            default -> false;
        };
        if (!allowed) {
            writeProjectAudit("LIFECYCLE_CHANGE_DENIED", resourceType, resourceId, projectId, "DENIED");
            throw new BusinessException(
                    ErrorCode.INVALID_STATE,
                    resourceType + " 生命周期不允许从 " + current + " 变更为 " + next
            );
        }
        return next;
    }

    private static Instant archivedAtFor(String lifecycleStatus, Instant existingArchivedAt, Instant now) {
        return "ARCHIVED".equals(lifecycleStatus) ? (existingArchivedAt == null ? now : existingArchivedAt) : null;
    }

    private static Instant deletedAtFor(String lifecycleStatus, Instant now) {
        return "DELETED".equals(lifecycleStatus) ? now : null;
    }

    private static String lifecycleAction(String lifecycleStatus) {
        return switch (lifecycleStatus) {
            case "ARCHIVED" -> "ARCHIVE";
            case "DELETED" -> "SOFT_DELETE";
            default -> "RESTORE";
        };
    }

    private static String lifecycleFilter(String rawValue) {
        return valueIn(rawValue, "ACTIVE", LIFECYCLE_STATUSES, "lifecycleStatus");
    }

    private static String lifecycleStatus(String lifecycleStatus, Instant deletedAt) {
        if (deletedAt != null) {
            return "DELETED";
        }
        return StringUtils.hasText(lifecycleStatus) ? lifecycleStatus : "ACTIVE";
    }

    private void ensureRequirementRestoreHasNoConflict(AssetRequirement requirement) {
        if (repository.hasActiveRequirementCodeConflict(requirement.projectId(), requirement.code(), requirement.id())) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下需求编码已被其他资产占用，无法恢复: " + requirement.code());
        }
        if ("IMPORT".equals(requirement.source()) && StringUtils.hasText(requirement.sourceRef())
                && repository.hasActiveRequirementSourceRefConflict(
                        requirement.projectId(),
                        requirement.source(),
                        requirement.sourceRef(),
                        requirement.id()
                )) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下导入来源已被其他需求占用，无法恢复: " + requirement.sourceRef());
        }
    }

    private void ensureApiRestoreHasNoConflict(AssetApi api) {
        if (repository.hasActiveApiPathConflict(api.projectId(), api.path(), api.httpMethod(), api.id())) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下 API 路径和方法已被其他资产占用，无法恢复");
        }
    }

    private void ensurePageRestoreHasNoConflict(AssetPage page) {
        if (repository.hasActivePageCodeConflict(page.projectId(), page.code(), page.id())) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下页面编码已被其他资产占用，无法恢复: " + page.code());
        }
    }

    private void ensureBusinessFlowRestoreHasNoConflict(AssetBusinessFlow flow) {
        if (repository.hasActiveBusinessFlowCodeConflict(flow.projectId(), flow.code(), flow.id())) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下业务流编码已被其他资产占用，无法恢复: " + flow.code());
        }
    }

    private void ensureTestCaseRestoreHasNoConflict(TestCaseRecord testCase) {
        if (repository.hasActiveTestCaseCodeConflict(testCase.projectId(), testCase.code(), testCase.id())) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下测试用例编码已被其他资产占用，无法恢复: " + testCase.code());
        }
    }

    private String nextRequirementStatus(AssetRequirement requirement, String rawNextStatus) {
        String nextStatus = valueIn(rawNextStatus, requirement.status(), REVIEW_STATUSES, "status");
        if (!requirement.canTransitionReviewStatusTo(nextStatus)) {
            rejectReviewStatusTransition(
                    "REQUIREMENT",
                    requirement.id(),
                    requirement.projectId(),
                    requirement.status(),
                    nextStatus
            );
        }
        return nextStatus;
    }

    private String nextTestCaseStatus(TestCaseRecord testCase, String rawNextStatus) {
        String nextStatus = valueIn(rawNextStatus, testCase.status(), REVIEW_STATUSES, "status");
        if (!testCase.canTransitionReviewStatusTo(nextStatus)) {
            rejectReviewStatusTransition(
                    "TEST_CASE",
                    testCase.id(),
                    testCase.projectId(),
                    testCase.status(),
                    nextStatus
            );
        }
        return nextStatus;
    }

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
            writeProjectAudit("STATUS_CHANGE_DENIED", resourceType, resourceId, projectId, "DENIED");
            throw new BusinessException(
                    ErrorCode.INVALID_STATE,
                    resourceType + " 状态不允许从 " + currentStatus + " 变更为 " + nextStatus
            );
        }
        return nextStatus;
    }

    private void rejectReviewStatusTransition(
            String resourceType,
            UUID resourceId,
            String projectId,
            String currentStatus,
            String nextStatus
    ) {
        writeProjectAudit("STATUS_CHANGE_DENIED", resourceType, resourceId, projectId, "DENIED");
        throw new BusinessException(
                ErrorCode.INVALID_STATE,
                resourceType + " 状态不允许从 " + currentStatus + " 变更为 " + nextStatus
        );
    }

    private static <T> com.songhg.veri.agent.common.api.PageResponse<T> page(List<T> items, int index, int size) {
        int from = Math.min(index * size, items.size());
        int to = Math.min(from + size, items.size());
        return com.songhg.veri.agent.common.api.PageResponse.of(items.subList(from, to), index, size, items.size());
    }

    private static String assetCode(String prefix, UUID id) {
        return prefix + "-" + id.toString().replace("-", "").substring(0, 12);
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

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }

    private static String mergeTags(String existing, String incoming) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addTags(tags, existing);
        addTags(tags, incoming);
        return tags.isEmpty() ? null : String.join(",", tags);
    }

    private static String normalizedTags(String rawTags) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addTags(tags, rawTags);
        return String.join(",", tags);
    }

    private static void addTags(LinkedHashSet<String> tags, String rawTags) {
        if (!StringUtils.hasText(rawTags)) {
            return;
        }
        for (String tag : rawTags.replace("，", ",").split(",")) {
            String trimmed = tag.trim();
            if (StringUtils.hasText(trimmed)) {
                tags.add(trimmed);
            }
        }
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
