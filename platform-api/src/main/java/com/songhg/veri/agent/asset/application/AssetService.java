package com.songhg.veri.agent.asset.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.command.AssetImportRequest;
import com.songhg.veri.agent.asset.application.command.AssetPrototypeSyncRequest;
import com.songhg.veri.agent.asset.application.command.CreateApiRequest;
import com.songhg.veri.agent.asset.application.command.CreateBusinessFlowRequest;
import com.songhg.veri.agent.asset.application.command.CreateLinkRequest;
import com.songhg.veri.agent.asset.application.command.CreatePageRequest;
import com.songhg.veri.agent.asset.application.command.CreateRequirementRequest;
import com.songhg.veri.agent.asset.application.command.CreateTestCaseRequest;
import com.songhg.veri.agent.asset.application.command.RollbackAssetVersionRequest;
import com.songhg.veri.agent.asset.application.command.UpdateApiRequest;
import com.songhg.veri.agent.asset.application.command.UpdateAssetLifecycleRequest;
import com.songhg.veri.agent.asset.application.command.UpdateBusinessFlowRequest;
import com.songhg.veri.agent.asset.application.command.UpdatePageRequest;
import com.songhg.veri.agent.asset.application.command.UpdateRequirementRequest;
import com.songhg.veri.agent.asset.application.command.UpdateTestCaseRequest;
import com.songhg.veri.agent.asset.application.command.UpdateTestCaseStepsRequest;
import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.application.port.PlatformContextClient;
import com.songhg.veri.agent.asset.application.query.AssetExportRequest;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.query.TraceLinkListRequest;
import com.songhg.veri.agent.asset.application.view.ApiResponseDTO;
import com.songhg.veri.agent.asset.application.view.AssetExportPayload;
import com.songhg.veri.agent.asset.application.view.AssetImpactAnalysisResponse;
import com.songhg.veri.agent.asset.application.view.AssetImportResponse;
import com.songhg.veri.agent.asset.application.view.AssetPrototypeSyncResponse;
import com.songhg.veri.agent.asset.application.view.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.application.view.BusinessFlowResponse;
import com.songhg.veri.agent.asset.application.view.PageResponse;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseStepResponse;
import com.songhg.veri.agent.asset.application.view.TraceLinkResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
public class AssetService {

    private final AssetRepository repository;
    private final ObjectMapper objectMapper;
    private final AssetProjectAuditService projectAuditService;
    private final AssetVersionHistoryService versionHistoryService;
    private final AssetImpactAnalysisService impactAnalysisService;
    private final AssetPrototypeSyncService prototypeSyncService;
    private final AssetTraceLinkService traceLinkService;
    private final AssetTestCaseStepService testCaseStepService;
    private final AssetRequirementService requirementService;
    private final AssetApiService apiService;
    private final AssetPageService pageService;
    private final AssetBusinessFlowService businessFlowService;
    private final AssetTestCaseService testCaseService;

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
        this.prototypeSyncService = new AssetPrototypeSyncService(
                repository,
                projectAuditService,
                objectMapper,
                versionHistoryService
        );
        this.traceLinkService = new AssetTraceLinkService(repository, projectAuditService);
        this.testCaseStepService = new AssetTestCaseStepService(
                repository,
                projectAuditService,
                versionHistoryService
        );
        AssetVersionRollbackService versionRollbackService = new AssetVersionRollbackService(
                repository,
                objectMapper,
                versionHistoryService,
                projectAuditService
        );
        AssetLifecycleService lifecycleService = new AssetLifecycleService(
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
        this.apiService = new AssetApiService(
                repository,
                projectAuditService,
                versionHistoryService,
                versionRollbackService,
                lifecycleService
        );
        this.pageService = new AssetPageService(
                repository,
                projectAuditService,
                versionHistoryService,
                versionRollbackService,
                lifecycleService,
                objectMapper
        );
        this.businessFlowService = new AssetBusinessFlowService(
                repository,
                projectAuditService,
                versionHistoryService,
                versionRollbackService,
                lifecycleService,
                objectMapper
        );
        this.testCaseService = new AssetTestCaseService(
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
            AssetApiService apiService,
            AssetPageService pageService,
            AssetBusinessFlowService businessFlowService,
            AssetTestCaseService testCaseService,
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
        this.requirementService = requirementService;
        this.apiService = apiService;
        this.pageService = pageService;
        this.businessFlowService = businessFlowService;
        this.testCaseService = testCaseService;
    }

    public String requirementProjectScopeId(UUID id) {
        return requirementService.requirementProjectScopeId(id);
    }

    public String apiProjectScopeId(UUID id) {
        return apiService.apiProjectScopeId(id);
    }

    public String pageProjectScopeId(UUID id) {
        return pageService.pageProjectScopeId(id);
    }

    public String businessFlowProjectScopeId(UUID id) {
        return businessFlowService.businessFlowProjectScopeId(id);
    }

    public String testCaseProjectScopeId(UUID id) {
        return testCaseService.testCaseProjectScopeId(id);
    }

    // ---- Requirements ----

    public com.songhg.veri.agent.common.api.PageResponse<RequirementResponse> listRequirements(
            AssetListRequest request
    ) {
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
        return apiService.listApis(request);
    }

    public ApiResponseDTO getApi(UUID id) {
        return apiService.getApi(id);
    }

    public ApiResponseDTO getApiIncludingInactive(UUID id) {
        return apiService.getApiIncludingInactive(id);
    }

    public ApiResponseDTO createApi(CreateApiRequest request) {
        return apiService.createApi(request);
    }

    public ApiResponseDTO updateApi(UUID id, UpdateApiRequest request) {
        return apiService.updateApi(id, request);
    }

    public List<AssetVersionHistoryResponse> apiVersions(UUID id) {
        return apiService.apiVersions(id);
    }

    @Transactional
    public ApiResponseDTO rollbackApiVersion(UUID id, int version, RollbackAssetVersionRequest request) {
        return apiService.rollbackApiVersion(id, version, request);
    }

    public ApiResponseDTO updateApiLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        return apiService.updateApiLifecycle(id, request);
    }

    // ---- Pages ----

    public com.songhg.veri.agent.common.api.PageResponse<PageResponse> listPages(AssetListRequest request) {
        return pageService.listPages(request);
    }

    public PageResponse getPage(UUID id) {
        return pageService.getPage(id);
    }

    public PageResponse getPageIncludingInactive(UUID id) {
        return pageService.getPageIncludingInactive(id);
    }

    public PageResponse createPage(CreatePageRequest request) {
        return pageService.createPage(request);
    }

    public PageResponse updatePage(UUID id, UpdatePageRequest request) {
        return pageService.updatePage(id, request);
    }

    public List<AssetVersionHistoryResponse> pageVersions(UUID id) {
        return pageService.pageVersions(id);
    }

    @Transactional
    public PageResponse rollbackPageVersion(UUID id, int version, RollbackAssetVersionRequest request) {
        return pageService.rollbackPageVersion(id, version, request);
    }

    public PageResponse updatePageLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        return pageService.updatePageLifecycle(id, request);
    }

    // ---- Business Flows ----

    public com.songhg.veri.agent.common.api.PageResponse<BusinessFlowResponse> listBusinessFlows(
            AssetListRequest request
    ) {
        return businessFlowService.listBusinessFlows(request);
    }

    public BusinessFlowResponse getBusinessFlow(UUID id) {
        return businessFlowService.getBusinessFlow(id);
    }

    public BusinessFlowResponse getBusinessFlowIncludingInactive(UUID id) {
        return businessFlowService.getBusinessFlowIncludingInactive(id);
    }

    public BusinessFlowResponse createBusinessFlow(CreateBusinessFlowRequest request) {
        return businessFlowService.createBusinessFlow(request);
    }

    public BusinessFlowResponse updateBusinessFlow(UUID id, UpdateBusinessFlowRequest request) {
        return businessFlowService.updateBusinessFlow(id, request);
    }

    public List<AssetVersionHistoryResponse> businessFlowVersions(UUID id) {
        return businessFlowService.businessFlowVersions(id);
    }

    @Transactional
    public BusinessFlowResponse rollbackBusinessFlowVersion(UUID id, int version, RollbackAssetVersionRequest request) {
        return businessFlowService.rollbackBusinessFlowVersion(id, version, request);
    }

    public BusinessFlowResponse updateBusinessFlowLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        return businessFlowService.updateBusinessFlowLifecycle(id, request);
    }

    // ---- Test Cases ----

    public com.songhg.veri.agent.common.api.PageResponse<TestCaseResponse> listTestCases(AssetListRequest request) {
        return testCaseService.listTestCases(request);
    }

    public TestCaseResponse getTestCase(UUID id) {
        return testCaseService.getTestCase(id);
    }

    public TestCaseResponse getTestCaseIncludingInactive(UUID id) {
        return testCaseService.getTestCaseIncludingInactive(id);
    }

    public Optional<TestCaseResponse> findTestCaseBySourceRef(String projectId, String source, String sourceRef) {
        return testCaseService.findTestCaseBySourceRef(projectId, source, sourceRef);
    }

    public List<TestCaseResponse> findActiveTestCasesByRequirement(String projectId, UUID requirementId) {
        return testCaseService.findActiveTestCasesByRequirement(projectId, requirementId);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TestCaseResponse createTestCase(CreateTestCaseRequest request) {
        return testCaseService.createTestCase(request);
    }

    @Transactional
    public TestCaseResponse updateTestCase(UUID id, UpdateTestCaseRequest request) {
        return testCaseService.updateTestCase(id, request);
    }

    public List<AssetVersionHistoryResponse> testCaseVersions(UUID id) {
        return testCaseService.testCaseVersions(id);
    }

    @Transactional
    public TestCaseResponse rollbackTestCaseVersion(
            UUID id,
            int version,
            RollbackAssetVersionRequest request
    ) {
        return testCaseService.rollbackTestCaseVersion(id, version, request);
    }

    @Transactional
    public TestCaseResponse updateTestCaseLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        return testCaseService.updateTestCaseLifecycle(id, request);
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
        return new AssetImportExportService(repository, projectAuditService, objectMapper, this, versionHistoryService);
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

}
