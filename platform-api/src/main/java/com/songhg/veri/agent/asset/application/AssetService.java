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
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
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
    private final ObjectMapper objectMapper;
    private final AssetProjectAuditService projectAuditService;
    private final AssetVersionHistoryService versionHistoryService;
    private final AssetImpactAnalysisService impactAnalysisService;
    private final AssetPrototypeSyncService prototypeSyncService;
    private final AssetTraceLinkService traceLinkService;
    private final AssetTestCaseStepService testCaseStepService;
    private final AssetVersionRollbackService versionRollbackService;
    private final AssetLifecycleService lifecycleService;
    private final AssetRequirementService requirementService;

    public AssetService(AssetRepository repository, PlatformContextClient contextClient) {
        this(repository, contextClient, new ObjectMapper().findAndRegisterModules());
    }

    public AssetService(AssetRepository repository, PlatformContextClient contextClient, ObjectMapper objectMapper) {
        this(repository, objectMapper, new AssetProjectAuditService(contextClient));
    }

    private AssetService(
            AssetRepository repository,
            ObjectMapper objectMapper,
            AssetProjectAuditService projectAuditService
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.projectAuditService = projectAuditService;
        this.versionHistoryService = new AssetVersionHistoryService(repository, objectMapper);
        this.impactAnalysisService = new AssetImpactAnalysisService(repository, projectAuditService);
        this.prototypeSyncService = new AssetPrototypeSyncService(repository, projectAuditService, objectMapper);
        this.traceLinkService = new AssetTraceLinkService(repository, projectAuditService);
        this.testCaseStepService = new AssetTestCaseStepService(
                repository,
                projectAuditService,
                versionHistoryService
        );
        this.versionRollbackService = new AssetVersionRollbackService(
                repository,
                objectMapper,
                versionHistoryService,
                projectAuditService
        );
        this.lifecycleService = new AssetLifecycleService(
                repository,
                projectAuditService,
                versionHistoryService
        );
        this.requirementService = new AssetRequirementService(
                repository,
                projectAuditService,
                versionHistoryService,
                versionRollbackService,
                lifecycleService
        );
    }

    @Autowired
    public AssetService(
            AssetRepository repository,
            ObjectMapper objectMapper,
            AssetVersionHistoryService versionHistoryService,
            AssetImpactAnalysisService impactAnalysisService,
            AssetPrototypeSyncService prototypeSyncService,
            AssetTraceLinkService traceLinkService,
            AssetTestCaseStepService testCaseStepService,
            AssetVersionRollbackService versionRollbackService,
            AssetLifecycleService lifecycleService,
            AssetRequirementService requirementService,
            AssetProjectAuditService projectAuditService
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.projectAuditService = projectAuditService;
        this.versionHistoryService = versionHistoryService;
        this.impactAnalysisService = impactAnalysisService;
        this.prototypeSyncService = prototypeSyncService;
        this.traceLinkService = traceLinkService;
        this.testCaseStepService = testCaseStepService;
        this.versionRollbackService = versionRollbackService;
        this.lifecycleService = lifecycleService;
        this.requirementService = requirementService;
    }

    public String resolveProjectScopeId(String projectId) {
        return projectContext(projectId).projectId();
    }

    public String requirementProjectScopeId(UUID id) {
        return requirementService.requirementProjectScopeId(id);
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
        return requirementService.listRequirements(request);
    }

    public RequirementResponse getRequirement(UUID id) {
        return requirementService.getRequirement(id);
    }

    public RequirementResponse getRequirementIncludingInactive(UUID id) {
        return requirementService.getRequirementIncludingInactive(id);
    }

    public Optional<RequirementResponse> findImportedRequirement(String projectId, String sourceRef) {
        return requirementService.findImportedRequirement(projectId, sourceRef);
    }

    @Transactional
    public RequirementResponse createRequirement(CreateRequirementRequest request) {
        return requirementService.createRequirement(request);
    }

    @Transactional
    public RequirementResponse updateRequirement(UUID id, UpdateRequirementRequest request) {
        return requirementService.updateRequirement(id, request);
    }

    public List<AssetVersionHistoryResponse> requirementVersions(UUID id) {
        return requirementService.requirementVersions(id);
    }

    @Transactional
    public RequirementResponse rollbackRequirementVersion(
            UUID id,
            int version,
            RollbackAssetVersionRequest request
    ) {
        return requirementService.rollbackRequirementVersion(id, version, request);
    }

    @Transactional
    public RequirementResponse updateRequirementLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        return requirementService.updateRequirementLifecycle(id, request);
    }

    // ---- APIs ----

    public com.songhg.veri.agent.common.api.PageResponse<ApiResponseDTO> listApis(AssetListRequest request) {
        validateProjectWhenProvided(request.getProjectId());
        AssetListQuery query = assetListQuery(request);
        List<ApiResponseDTO> items = repository.apis(query).stream()
                .map(AssetResponseMapper::toApiResponse)
                .toList();
        return com.songhg.veri.agent.common.api.PageResponse.of(items, query.index(), query.size(), repository.countApis(query));
    }

    public ApiResponseDTO getApi(UUID id) {
        return repository.api(id)
                .map(AssetResponseMapper::toApiResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + id));
    }

    public ApiResponseDTO getApiIncludingInactive(UUID id) {
        AssetApi api = repository.apiIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + id));
        validateProjectWhenProvided(api.projectId());
        return AssetResponseMapper.toApiResponse(api);
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
        return AssetResponseMapper.toApiResponse(api);
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
        return AssetResponseMapper.toApiResponse(updated);
    }

    public ApiResponseDTO updateApiLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        return lifecycleService.updateApiLifecycle(id, request);
    }

    // ---- Pages ----

    public com.songhg.veri.agent.common.api.PageResponse<PageResponse> listPages(AssetListRequest request) {
        validateProjectWhenProvided(request.getProjectId());
        AssetListQuery query = assetListQuery(request);
        List<PageResponse> items = repository.pages(query).stream()
                .map(AssetResponseMapper::toPageResponse)
                .toList();
        return com.songhg.veri.agent.common.api.PageResponse.of(items, query.index(), query.size(), repository.countPages(query));
    }

    public PageResponse getPage(UUID id) {
        return repository.page(id)
                .map(AssetResponseMapper::toPageResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + id));
    }

    public PageResponse getPageIncludingInactive(UUID id) {
        AssetPage page = repository.pageIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + id));
        validateProjectWhenProvided(page.projectId());
        return AssetResponseMapper.toPageResponse(page);
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
        return AssetResponseMapper.toPageResponse(page);
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
        return AssetResponseMapper.toPageResponse(updated);
    }

    public PageResponse updatePageLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        return lifecycleService.updatePageLifecycle(id, request);
    }

    // ---- Business Flows ----

    public com.songhg.veri.agent.common.api.PageResponse<BusinessFlowResponse> listBusinessFlows(AssetListRequest request) {
        validateProjectWhenProvided(request.getProjectId());
        AssetListQuery query = assetListQuery(request);
        List<BusinessFlowResponse> items = repository.businessFlows(query).stream()
                .map(AssetResponseMapper::toBusinessFlowResponse)
                .toList();
        return com.songhg.veri.agent.common.api.PageResponse.of(items, query.index(), query.size(), repository.countBusinessFlows(query));
    }

    public BusinessFlowResponse getBusinessFlow(UUID id) {
        return repository.businessFlow(id)
                .map(AssetResponseMapper::toBusinessFlowResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流资产不存在: " + id));
    }

    public BusinessFlowResponse getBusinessFlowIncludingInactive(UUID id) {
        AssetBusinessFlow flow = repository.businessFlowIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流资产不存在: " + id));
        validateProjectWhenProvided(flow.projectId());
        return AssetResponseMapper.toBusinessFlowResponse(flow);
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
        return AssetResponseMapper.toBusinessFlowResponse(flow);
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
        return AssetResponseMapper.toBusinessFlowResponse(updated);
    }

    public BusinessFlowResponse updateBusinessFlowLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        return lifecycleService.updateBusinessFlowLifecycle(id, request);
    }

    // ---- Test Cases ----

    public com.songhg.veri.agent.common.api.PageResponse<TestCaseResponse> listTestCases(AssetListRequest request) {
        validateProjectWhenProvided(request.getProjectId());
        AssetListQuery query = assetListQuery(request);
        List<TestCaseResponse> items = repository.testCases(query).stream()
                .map(tc -> AssetResponseMapper.toTestCaseResponse(tc, tc.steps()))
                .toList();
        return com.songhg.veri.agent.common.api.PageResponse.of(items, query.index(), query.size(), repository.countTestCases(query));
    }

    public TestCaseResponse getTestCase(UUID id) {
        TestCaseRecord testCase = repository.testCase(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        return AssetResponseMapper.toTestCaseResponse(testCase, testCase.steps());
    }

    public TestCaseResponse getTestCaseIncludingInactive(UUID id) {
        TestCaseRecord testCase = repository.testCaseIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        validateProjectWhenProvided(testCase.projectId());
        return AssetResponseMapper.toTestCaseResponse(testCase, testCase.steps());
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
        return AssetResponseMapper.toTestCaseResponse(stored, steps);
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
        return AssetResponseMapper.toTestCaseResponse(stored, existingSteps);
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
        return versionRollbackService.rollbackTestCaseVersion(id, version, request);
    }

    @Transactional
    public TestCaseResponse updateTestCaseLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        return lifecycleService.updateTestCaseLifecycle(id, request);
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
        return new AssetImportExportService(repository, projectAuditService, objectMapper, this);
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

    private void validateProjectWhenProvided(String projectId) {
        projectAuditService.validateProjectWhenProvided(projectId);
    }

    private PlatformContextClient.ProjectContext projectContext(String projectId) {
        return projectAuditService.projectContext(projectId);
    }

    private void writeProjectAudit(String action, String resourceType, UUID resourceId, String projectId) {
        projectAuditService.writeProjectAudit(action, resourceType, resourceId, projectId);
    }

    private void writeProjectAudit(String action, String resourceType, UUID resourceId, String projectId, String result) {
        projectAuditService.writeProjectAudit(action, resourceType, resourceId, projectId, result);
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

    private static String lifecycleFilter(String rawValue) {
        return valueIn(rawValue, "ACTIVE", LIFECYCLE_STATUSES, "lifecycleStatus");
    }

    private static String lifecycleStatus(String lifecycleStatus, Instant deletedAt) {
        return AssetLifecycleStatus.normalize(lifecycleStatus, deletedAt);
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
