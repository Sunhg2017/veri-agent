package com.songhg.veri.agent.asset.api.controller;

import com.songhg.veri.agent.asset.application.AssetListRequest;
import com.songhg.veri.agent.asset.application.AssetExportRequest;
import com.songhg.veri.agent.asset.application.AssetImportRequest;
import com.songhg.veri.agent.asset.application.AssetPrototypeSyncRequest;
import com.songhg.veri.agent.asset.application.CreateApiRequest;
import com.songhg.veri.agent.asset.application.CreateBusinessFlowRequest;
import com.songhg.veri.agent.asset.application.CreateLinkRequest;
import com.songhg.veri.agent.asset.application.CreatePageRequest;
import com.songhg.veri.agent.asset.application.CreateRequirementRequest;
import com.songhg.veri.agent.asset.application.RollbackAssetVersionRequest;
import com.songhg.veri.agent.asset.application.CreateTestCaseRequest;
import com.songhg.veri.agent.asset.application.TraceLinkListRequest;
import com.songhg.veri.agent.asset.application.UpdateApiRequest;
import com.songhg.veri.agent.asset.application.UpdateAssetLifecycleRequest;
import com.songhg.veri.agent.asset.application.UpdateBusinessFlowRequest;
import com.songhg.veri.agent.asset.application.UpdatePageRequest;
import com.songhg.veri.agent.asset.application.UpdateRequirementRequest;
import com.songhg.veri.agent.asset.application.UpdateTestCaseRequest;
import com.songhg.veri.agent.asset.application.UpdateTestCaseStepsRequest;
import com.songhg.veri.agent.asset.application.ApiResponseDTO;
import com.songhg.veri.agent.asset.application.AssetExportPayload;
import com.songhg.veri.agent.asset.application.AssetImpactAnalysisResponse;
import com.songhg.veri.agent.asset.application.AssetImportResponse;
import com.songhg.veri.agent.asset.application.AssetPrototypeSyncResponse;
import com.songhg.veri.agent.asset.application.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.application.BusinessFlowResponse;
import com.songhg.veri.agent.asset.application.PageResponse;
import com.songhg.veri.agent.asset.application.RequirementResponse;
import com.songhg.veri.agent.asset.application.TestCaseResponse;
import com.songhg.veri.agent.asset.application.TestCaseStepResponse;
import com.songhg.veri.agent.asset.application.TraceLinkResponse;
import com.songhg.veri.agent.asset.application.AssetImportExportService;
import com.songhg.veri.agent.asset.application.AssetPrototypeSyncService;
import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/asset")
public class AssetController {

    private static final String ASSET_READ = "asset:read";
    private static final String ASSET_MANAGE = "asset:manage";
    private static final String ASSET_EXPORT = "asset:export";
    private static final String PROJECT_REQUEST_SCOPE = "@assetPermissionScopeResolver.project(#request.projectId())";
    private static final String PROJECT_QUERY_SCOPE = "@assetPermissionScopeResolver.project(#projectId)";
    private static final String ASSET_LIST_SCOPE = "@assetPermissionScopeResolver.assetList(#request)";
    private static final String REQUIREMENT_SCOPE = "@assetPermissionScopeResolver.requirement(#id)";
    private static final String API_SCOPE = "@assetPermissionScopeResolver.api(#id)";
    private static final String PAGE_SCOPE = "@assetPermissionScopeResolver.page(#id)";
    private static final String BUSINESS_FLOW_SCOPE = "@assetPermissionScopeResolver.businessFlow(#id)";
    private static final String TEST_CASE_SCOPE = "@assetPermissionScopeResolver.testCase(#id)";
    private static final String TRACE_LINK_LIST_SCOPE = "@assetPermissionScopeResolver.traceLinkList(#request)";
    private static final String CREATE_LINK_SCOPE =
            "@assetPermissionScopeResolver.requirement(#request.requirementId())";

    private final AssetService service;
    private final AssetImportExportService importExportService;
    private final AssetPrototypeSyncService prototypeSyncService;

    public AssetController(
            AssetService service,
            AssetImportExportService importExportService,
            AssetPrototypeSyncService prototypeSyncService
    ) {
        this.service = service;
        this.importExportService = importExportService;
        this.prototypeSyncService = prototypeSyncService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("service", "asset-service", "status", service.health());
    }

    @PostMapping("/imports")
    @RequirePermission(value = ASSET_MANAGE, scope = PROJECT_REQUEST_SCOPE)
    public AssetImportResponse importAssets(@Valid @RequestBody AssetImportRequest request) {
        return importExportService.importAssets(request);
    }

    @GetMapping("/exports")
    @RequirePermission(value = ASSET_EXPORT, scope = ASSET_LIST_SCOPE)
    public ResponseEntity<byte[]> exportAssets(@Valid AssetExportRequest request) {
        AssetExportPayload payload = importExportService.exportAssets(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.fileName() + "\"")
                .body(payload.content());
    }

    @PostMapping("/prototype-sync")
    @RequirePermission(value = ASSET_MANAGE, scope = PROJECT_REQUEST_SCOPE)
    public AssetPrototypeSyncResponse syncPrototypePages(@Valid @RequestBody AssetPrototypeSyncRequest request) {
        return prototypeSyncService.syncPrototypePages(request);
    }

    @GetMapping("/impact")
    @RequirePermission(value = ASSET_READ, scope = PROJECT_QUERY_SCOPE)
    public AssetImpactAnalysisResponse analyzeImpact(
            @RequestParam String projectId,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) UUID assetId
    ) {
        return service.analyzeImpact(projectId, assetType, assetId);
    }

    // ---- Requirements ----

    @GetMapping("/requirements")
    @RequirePermission(value = ASSET_READ, scope = ASSET_LIST_SCOPE)
    public com.songhg.veri.agent.common.api.PageResponse<RequirementResponse> listRequirements(
            @Valid AssetListRequest request
    ) {
        return service.listRequirements(request);
    }

    @PostMapping("/requirements")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = ASSET_MANAGE, scope = PROJECT_REQUEST_SCOPE)
    public RequirementResponse createRequirement(@Valid @RequestBody CreateRequirementRequest request) {
        return service.createRequirement(request);
    }

    @GetMapping("/requirements/{id}")
    @RequirePermission(value = ASSET_READ, scope = REQUIREMENT_SCOPE)
    public RequirementResponse getRequirement(@PathVariable UUID id) {
        return service.getRequirement(id);
    }

    @GetMapping("/requirements/{id}/lifecycle")
    @RequirePermission(value = ASSET_READ, scope = REQUIREMENT_SCOPE)
    public RequirementResponse getRequirementLifecycle(@PathVariable UUID id) {
        return service.getRequirementIncludingInactive(id);
    }

    @PutMapping("/requirements/{id}")
    @RequirePermission(value = ASSET_MANAGE, scope = REQUIREMENT_SCOPE)
    public RequirementResponse updateRequirement(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRequirementRequest request
    ) {
        return service.updateRequirement(id, request);
    }

    @PatchMapping("/requirements/{id}/lifecycle")
    @RequirePermission(value = ASSET_MANAGE, scope = REQUIREMENT_SCOPE)
    public RequirementResponse updateRequirementLifecycle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetLifecycleRequest request
    ) {
        return service.updateRequirementLifecycle(id, request);
    }

    @GetMapping("/requirements/{id}/versions")
    @RequirePermission(value = ASSET_READ, scope = REQUIREMENT_SCOPE)
    public List<AssetVersionHistoryResponse> requirementVersions(@PathVariable UUID id) {
        return service.requirementVersions(id);
    }

    @PostMapping("/requirements/{id}/versions/{version}/rollback")
    @RequirePermission(value = ASSET_MANAGE, scope = REQUIREMENT_SCOPE)
    public RequirementResponse rollbackRequirementVersion(
            @PathVariable UUID id,
            @PathVariable int version,
            @Valid @RequestBody(required = false) RollbackAssetVersionRequest request
    ) {
        return service.rollbackRequirementVersion(id, version, request);
    }

    // ---- APIs ----

    @GetMapping("/apis")
    @RequirePermission(value = ASSET_READ, scope = ASSET_LIST_SCOPE)
    public com.songhg.veri.agent.common.api.PageResponse<ApiResponseDTO> listApis(
            @Valid AssetListRequest request
    ) {
        return service.listApis(request);
    }

    @PostMapping("/apis")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = ASSET_MANAGE, scope = PROJECT_REQUEST_SCOPE)
    public ApiResponseDTO createApi(@Valid @RequestBody CreateApiRequest request) {
        return service.createApi(request);
    }

    @GetMapping("/apis/{id}")
    @RequirePermission(value = ASSET_READ, scope = API_SCOPE)
    public ApiResponseDTO getApi(@PathVariable UUID id) {
        return service.getApi(id);
    }

    @GetMapping("/apis/{id}/lifecycle")
    @RequirePermission(value = ASSET_READ, scope = API_SCOPE)
    public ApiResponseDTO getApiLifecycle(@PathVariable UUID id) {
        return service.getApiIncludingInactive(id);
    }

    @PutMapping("/apis/{id}")
    @RequirePermission(value = ASSET_MANAGE, scope = API_SCOPE)
    public ApiResponseDTO updateApi(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApiRequest request
    ) {
        return service.updateApi(id, request);
    }

    @PatchMapping("/apis/{id}/lifecycle")
    @RequirePermission(value = ASSET_MANAGE, scope = API_SCOPE)
    public ApiResponseDTO updateApiLifecycle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetLifecycleRequest request
    ) {
        return service.updateApiLifecycle(id, request);
    }

    // ---- Pages ----

    @GetMapping("/pages")
    @RequirePermission(value = ASSET_READ, scope = ASSET_LIST_SCOPE)
    public com.songhg.veri.agent.common.api.PageResponse<PageResponse> listPages(
            @Valid AssetListRequest request
    ) {
        return service.listPages(request);
    }

    @PostMapping("/pages")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = ASSET_MANAGE, scope = PROJECT_REQUEST_SCOPE)
    public PageResponse createPage(@Valid @RequestBody CreatePageRequest request) {
        return service.createPage(request);
    }

    @GetMapping("/pages/{id}")
    @RequirePermission(value = ASSET_READ, scope = PAGE_SCOPE)
    public PageResponse getPage(@PathVariable UUID id) {
        return service.getPage(id);
    }

    @GetMapping("/pages/{id}/lifecycle")
    @RequirePermission(value = ASSET_READ, scope = PAGE_SCOPE)
    public PageResponse getPageLifecycle(@PathVariable UUID id) {
        return service.getPageIncludingInactive(id);
    }

    @PutMapping("/pages/{id}")
    @RequirePermission(value = ASSET_MANAGE, scope = PAGE_SCOPE)
    public PageResponse updatePage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePageRequest request
    ) {
        return service.updatePage(id, request);
    }

    @PatchMapping("/pages/{id}/lifecycle")
    @RequirePermission(value = ASSET_MANAGE, scope = PAGE_SCOPE)
    public PageResponse updatePageLifecycle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetLifecycleRequest request
    ) {
        return service.updatePageLifecycle(id, request);
    }

    // ---- Business Flows ----

    @GetMapping("/business-flows")
    @RequirePermission(value = ASSET_READ, scope = ASSET_LIST_SCOPE)
    public com.songhg.veri.agent.common.api.PageResponse<BusinessFlowResponse> listBusinessFlows(
            @Valid AssetListRequest request
    ) {
        return service.listBusinessFlows(request);
    }

    @PostMapping("/business-flows")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = ASSET_MANAGE, scope = PROJECT_REQUEST_SCOPE)
    public BusinessFlowResponse createBusinessFlow(@Valid @RequestBody CreateBusinessFlowRequest request) {
        return service.createBusinessFlow(request);
    }

    @GetMapping("/business-flows/{id}")
    @RequirePermission(value = ASSET_READ, scope = BUSINESS_FLOW_SCOPE)
    public BusinessFlowResponse getBusinessFlow(@PathVariable UUID id) {
        return service.getBusinessFlow(id);
    }

    @GetMapping("/business-flows/{id}/lifecycle")
    @RequirePermission(value = ASSET_READ, scope = BUSINESS_FLOW_SCOPE)
    public BusinessFlowResponse getBusinessFlowLifecycle(@PathVariable UUID id) {
        return service.getBusinessFlowIncludingInactive(id);
    }

    @PutMapping("/business-flows/{id}")
    @RequirePermission(value = ASSET_MANAGE, scope = BUSINESS_FLOW_SCOPE)
    public BusinessFlowResponse updateBusinessFlow(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBusinessFlowRequest request
    ) {
        return service.updateBusinessFlow(id, request);
    }

    @PatchMapping("/business-flows/{id}/lifecycle")
    @RequirePermission(value = ASSET_MANAGE, scope = BUSINESS_FLOW_SCOPE)
    public BusinessFlowResponse updateBusinessFlowLifecycle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetLifecycleRequest request
    ) {
        return service.updateBusinessFlowLifecycle(id, request);
    }

    // ---- Test Cases ----

    @GetMapping("/test-cases")
    @RequirePermission(value = ASSET_READ, scope = ASSET_LIST_SCOPE)
    public com.songhg.veri.agent.common.api.PageResponse<TestCaseResponse> listTestCases(
            @Valid AssetListRequest request
    ) {
        return service.listTestCases(request);
    }

    @PostMapping("/test-cases")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = ASSET_MANAGE, scope = PROJECT_REQUEST_SCOPE)
    public TestCaseResponse createTestCase(@Valid @RequestBody CreateTestCaseRequest request) {
        return service.createTestCase(request);
    }

    @GetMapping("/test-cases/{id}")
    @RequirePermission(value = ASSET_READ, scope = TEST_CASE_SCOPE)
    public TestCaseResponse getTestCase(@PathVariable UUID id) {
        return service.getTestCase(id);
    }

    @GetMapping("/test-cases/{id}/lifecycle")
    @RequirePermission(value = ASSET_READ, scope = TEST_CASE_SCOPE)
    public TestCaseResponse getTestCaseLifecycle(@PathVariable UUID id) {
        return service.getTestCaseIncludingInactive(id);
    }

    @PutMapping("/test-cases/{id}")
    @RequirePermission(value = ASSET_MANAGE, scope = TEST_CASE_SCOPE)
    public TestCaseResponse updateTestCase(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestCaseRequest request
    ) {
        return service.updateTestCase(id, request);
    }

    @PatchMapping("/test-cases/{id}/lifecycle")
    @RequirePermission(value = ASSET_MANAGE, scope = TEST_CASE_SCOPE)
    public TestCaseResponse updateTestCaseLifecycle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetLifecycleRequest request
    ) {
        return service.updateTestCaseLifecycle(id, request);
    }

    @GetMapping("/test-cases/{id}/versions")
    @RequirePermission(value = ASSET_READ, scope = TEST_CASE_SCOPE)
    public List<AssetVersionHistoryResponse> testCaseVersions(@PathVariable UUID id) {
        return service.testCaseVersions(id);
    }

    @PostMapping("/test-cases/{id}/versions/{version}/rollback")
    @RequirePermission(value = ASSET_MANAGE, scope = TEST_CASE_SCOPE)
    public TestCaseResponse rollbackTestCaseVersion(
            @PathVariable UUID id,
            @PathVariable int version,
            @Valid @RequestBody(required = false) RollbackAssetVersionRequest request
    ) {
        return service.rollbackTestCaseVersion(id, version, request);
    }

    @GetMapping("/test-cases/{id}/steps")
    @RequirePermission(value = ASSET_READ, scope = TEST_CASE_SCOPE)
    public List<TestCaseStepResponse> listTestCaseSteps(@PathVariable UUID id) {
        return service.listTestCaseSteps(id);
    }

    @PutMapping("/test-cases/{id}/steps")
    @RequirePermission(value = ASSET_MANAGE, scope = TEST_CASE_SCOPE)
    public List<TestCaseStepResponse> updateTestCaseSteps(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestCaseStepsRequest request
    ) {
        return service.updateTestCaseSteps(id, request);
    }

    // ---- Trace Links ----

    @GetMapping("/links")
    @RequirePermission(value = ASSET_READ, scope = TRACE_LINK_LIST_SCOPE)
    public com.songhg.veri.agent.common.api.PageResponse<TraceLinkResponse> listLinks(
            @Valid TraceLinkListRequest request
    ) {
        return service.listLinks(request);
    }

    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = ASSET_MANAGE, scope = CREATE_LINK_SCOPE)
    public TraceLinkResponse createLink(@Valid @RequestBody CreateLinkRequest request) {
        return service.createLink(request);
    }
}
