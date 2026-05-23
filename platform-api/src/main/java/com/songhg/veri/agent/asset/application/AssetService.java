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
import com.songhg.veri.agent.asset.api.response.AssetImportResponse;
import com.songhg.veri.agent.asset.api.response.AssetPrototypeSyncResponse;
import com.songhg.veri.agent.asset.api.response.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.api.response.BusinessFlowResponse;
import com.songhg.veri.agent.asset.api.response.PageResponse;
import com.songhg.veri.agent.asset.api.response.RequirementResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseStepResponse;
import com.songhg.veri.agent.asset.api.response.TraceLinkResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetLifecycleStatus;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.AssetReviewStatus;
import com.songhg.veri.agent.asset.domain.AssetVersion;
import com.songhg.veri.agent.asset.domain.AssetVersionHistory;
import com.songhg.veri.agent.asset.domain.LifecycleManagedAsset;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);
    private static final String SOURCE_IMPORT = "IMPORT";
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final Set<String> REVIEW_STATUSES = AssetReviewStatus.codes();
    private static final Set<String> LIFECYCLE_STATUSES = AssetLifecycleStatus.codes();
    private static final Set<String> PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<String> REQUIREMENT_SOURCES = Set.of(SOURCE_IMPORT, SOURCE_MANUAL);
    private static final Set<String> API_STATUSES = Set.of(STATUS_ACTIVE, "DEPRECATED", "REMOVED");
    private static final Set<String> API_SOURCES = Set.of("OPENAPI", SOURCE_MANUAL, SOURCE_IMPORT);
    private static final Set<String> API_HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final Set<String> PAGE_STATUSES = Set.of(STATUS_ACTIVE, "DEPRECATED");
    private static final Set<String> PAGE_SOURCES = Set.of("FIGMA", "LANHU", "AXURE", SOURCE_MANUAL);
    private static final Set<String> FLOW_STATUSES = Set.of(STATUS_DRAFT, STATUS_ACTIVE, "ARCHIVED");
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
    private final AssetVersionHistoryService versionHistoryService;
    private final AssetImpactAnalysisService impactAnalysisService;
    private final AssetPrototypeSyncService prototypeSyncService;
    private final AssetTraceLinkService traceLinkService;
    private final AssetTestCaseStepService testCaseStepService;

    public AssetService(AssetRepository repository, PlatformContextClient contextClient) {
        this(repository, contextClient, new ObjectMapper().findAndRegisterModules());
    }

    public AssetService(AssetRepository repository, PlatformContextClient contextClient, ObjectMapper objectMapper) {
        this(
                repository,
                contextClient,
                objectMapper,
                new AssetVersionHistoryService(repository, objectMapper),
                new AssetImpactAnalysisService(repository, contextClient),
                new AssetPrototypeSyncService(repository, contextClient, objectMapper),
                new AssetTraceLinkService(repository, contextClient),
                new AssetTestCaseStepService(
                        repository,
                        contextClient,
                        new AssetVersionHistoryService(repository, objectMapper)
                )
        );
    }

    @Autowired
    public AssetService(
            AssetRepository repository,
            PlatformContextClient contextClient,
            ObjectMapper objectMapper,
            AssetVersionHistoryService versionHistoryService,
            AssetImpactAnalysisService impactAnalysisService,
            AssetPrototypeSyncService prototypeSyncService,
            AssetTraceLinkService traceLinkService,
            AssetTestCaseStepService testCaseStepService
    ) {
        this.repository = repository;
        this.contextClient = contextClient;
        this.objectMapper = objectMapper;
        this.versionHistoryService = versionHistoryService;
        this.impactAnalysisService = impactAnalysisService;
        this.prototypeSyncService = prototypeSyncService;
        this.traceLinkService = traceLinkService;
        this.testCaseStepService = testCaseStepService;
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
                AssetRequirement stored = repository.saveRequirement(merged);
                versionHistoryService.recordRequirementChange(existing.get(), stored, "UPSERT");
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
                AssetVersion.initial(),
                "ACTIVE",
                null,
                null,
                now,
                now
        );
        writeProjectAudit("CREATE", "REQUIREMENT", id, scopeId);
        AssetRequirement stored = repository.saveRequirement(req);
        if (stored.id().equals(id)) {
            versionHistoryService.recordRequirementCreated(stored);
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
                existing.nextVersion(),
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
                existing.nextVersion(),
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
        writeProjectAudit("UPDATE", "REQUIREMENT", id, existing.projectId());
        AssetRequirement stored = repository.saveRequirement(updated);
        versionHistoryService.recordRequirementChange(existing, stored, "UPDATE");
        return toRequirementResponse(stored);
    }

    public List<AssetVersionHistoryResponse> requirementVersions(UUID id) {
        AssetRequirement requirement = repository.requirementIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
        return versionHistoryService.responses("REQUIREMENT", requirement.id());
    }

    @Transactional
    public RequirementResponse rollbackRequirementVersion(
            UUID id,
            int version,
            RollbackAssetVersionRequest request
    ) {
        AssetRequirement existing = repository.requirementIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
        AssetVersionHistory history = versionHistoryService.historyOrThrow("REQUIREMENT", id, version);
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
                existing.nextVersion(),
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
        versionHistoryService.recordRequirementChange(existing, stored, "ROLLBACK");
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
                existing,
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
                existing.nextVersion(),
                nextLifecycle,
                archivedAtFor(nextLifecycle, existing.archivedAt(), now),
                deletedAtFor(nextLifecycle, now),
                existing.createdAt(),
                now
        );
        writeProjectAudit(lifecycleAction(nextLifecycle), "REQUIREMENT", id, existing.projectId());
        AssetRequirement stored = repository.saveRequirement(updated);
        versionHistoryService.recordRequirementChange(existing, stored, lifecycleAction(nextLifecycle));
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
                existing,
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
                existing,
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
                existing,
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
                AssetVersion.initial(),
                "ACTIVE",
                null,
                null,
                now,
                now
        );
        writeProjectAudit("CREATE", "TEST_CASE", id, scopeId);
        TestCaseRecord stored = repository.saveTestCase(tc);
        versionHistoryService.recordTestCaseCreated(stored);
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
                existing.nextVersion(),
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
        writeProjectAudit("UPDATE", "TEST_CASE", id, existing.projectId());
        TestCaseRecord stored = repository.saveTestCase(updated);
        versionHistoryService.recordTestCaseChange(existing, stored, "UPDATE");
        return toTestCaseResponse(stored, existingSteps);
    }

    public List<AssetVersionHistoryResponse> testCaseVersions(UUID id) {
        TestCaseRecord testCase = repository.testCaseIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        return versionHistoryService.responses("TEST_CASE", testCase.id());
    }

    @Transactional
    public TestCaseResponse rollbackTestCaseVersion(
            UUID id,
            int version,
            RollbackAssetVersionRequest request
    ) {
        TestCaseRecord existing = repository.testCaseIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        AssetVersionHistory history = versionHistoryService.historyOrThrow("TEST_CASE", id, version);
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
                existing.nextVersion(),
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
        versionHistoryService.recordTestCaseChange(existing, stored, "ROLLBACK");
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
                existing,
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
                existing.nextVersion(),
                nextLifecycle,
                archivedAtFor(nextLifecycle, existing.archivedAt(), now),
                deletedAtFor(nextLifecycle, now),
                existing.createdAt(),
                now
        );
        writeProjectAudit(lifecycleAction(nextLifecycle), "TEST_CASE", id, existing.projectId());
        TestCaseRecord stored = repository.saveTestCase(updated);
        versionHistoryService.recordTestCaseChange(existing, stored, lifecycleAction(nextLifecycle));
        return toTestCaseResponse(stored, stored.steps());
    }

    // ---- Test Case Steps ----

    public List<TestCaseStepResponse> listTestCaseSteps(UUID caseId) {
        return testCaseStepService.listTestCaseSteps(caseId);
    }

    @Transactional
    public List<TestCaseStepResponse> updateTestCaseSteps(UUID caseId, UpdateTestCaseStepsRequest request) {
        return testCaseStepService.updateTestCaseSteps(caseId, request);
    }

    // ---- Trace Links ----

    public com.songhg.veri.agent.common.api.PageResponse<TraceLinkResponse> listLinks(TraceLinkListRequest request) {
        return traceLinkService.listLinks(request);
    }

    public TraceLinkResponse createLink(CreateLinkRequest request) {
        return traceLinkService.createLink(request);
    }

    // ---- Import / Export ----

    public AssetImportResponse importAssets(AssetImportRequest request) {
        return importExportService().importAssets(request);
    }

    public AssetExportPayload exportAssets(AssetExportRequest request) {
        return importExportService().exportAssets(request);
    }

    private AssetImportExportService importExportService() {
        return new AssetImportExportService(repository, contextClient, objectMapper, this);
    }

    // ---- Prototype sync / impact analysis ----

    public AssetPrototypeSyncResponse syncPrototypePages(AssetPrototypeSyncRequest request) {
        return prototypeSyncService.syncPrototypePages(request);
    }

    public AssetImpactAnalysisResponse analyzeImpact(String projectId, String rawSubjectType, UUID subjectId) {
        return impactAnalysisService.analyzeImpact(projectId, rawSubjectType, subjectId);
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

    private static String snapshotText(JsonNode snapshot, String field) {
        JsonNode node = snapshot.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static String textOrDefault(JsonNode node, String defaultValue) {
        String value = node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
        return StringUtils.hasText(value) ? value : defaultValue;
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
            LifecycleManagedAsset asset,
            String rawNextLifecycle
    ) {
        String current = asset.currentLifecycleStatus();
        String next = valueIn(rawNextLifecycle, current, LIFECYCLE_STATUSES, "lifecycleStatus");
        if (!asset.canTransitionLifecycleTo(next)) {
            writeProjectAudit("LIFECYCLE_CHANGE_DENIED", resourceType, asset.id(), asset.projectId(), "DENIED");
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
        return AssetLifecycleStatus.normalize(lifecycleStatus, deletedAt);
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
