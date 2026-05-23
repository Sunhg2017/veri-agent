package com.songhg.veri.agent.asset.api.controller;

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
import com.songhg.veri.agent.asset.application.AssetImportExportService;
import com.songhg.veri.agent.asset.application.AssetPrototypeSyncService;
import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.authorization.application.ResourceScope;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
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

    private final AssetService service;
    private final AssetImportExportService importExportService;
    private final AssetPrototypeSyncService prototypeSyncService;
    private final AuthorizationService authorizationService;

    public AssetController(
            AssetService service,
            AssetImportExportService importExportService,
            AssetPrototypeSyncService prototypeSyncService,
            AuthorizationService authorizationService
    ) {
        this.service = service;
        this.importExportService = importExportService;
        this.prototypeSyncService = prototypeSyncService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("service", "asset-service", "status", service.health());
    }

    @PostMapping("/imports")
    public AssetImportResponse importAssets(@Valid @RequestBody AssetImportRequest request) {
        requireProjectPermission("asset:manage", request.projectId());
        return importExportService.importAssets(request);
    }

    @GetMapping("/exports")
    public ResponseEntity<byte[]> exportAssets(@Valid AssetExportRequest request) {
        requireListPermission("asset:export", request);
        AssetExportPayload payload = importExportService.exportAssets(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.fileName() + "\"")
                .body(payload.content());
    }

    @PostMapping("/prototype-sync")
    public AssetPrototypeSyncResponse syncPrototypePages(@Valid @RequestBody AssetPrototypeSyncRequest request) {
        requireProjectPermission("asset:manage", request.projectId());
        return prototypeSyncService.syncPrototypePages(request);
    }

    @GetMapping("/impact")
    public AssetImpactAnalysisResponse analyzeImpact(
            @RequestParam String projectId,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) UUID assetId
    ) {
        requireProjectPermission("asset:read", projectId);
        return service.analyzeImpact(projectId, assetType, assetId);
    }

    // ---- Requirements ----

    @GetMapping("/requirements")
    public com.songhg.veri.agent.common.api.PageResponse<RequirementResponse> listRequirements(
            @Valid AssetListRequest request
    ) {
        requireListPermission("asset:read", request);
        return service.listRequirements(request);
    }

    @PostMapping("/requirements")
    @ResponseStatus(HttpStatus.CREATED)
    public RequirementResponse createRequirement(@Valid @RequestBody CreateRequirementRequest request) {
        requireProjectPermission("asset:manage", request.projectId());
        return service.createRequirement(request);
    }

    @GetMapping("/requirements/{id}")
    public RequirementResponse getRequirement(@PathVariable UUID id) {
        requireRequirementPermission("asset:read", id);
        return service.getRequirement(id);
    }

    @GetMapping("/requirements/{id}/lifecycle")
    public RequirementResponse getRequirementLifecycle(@PathVariable UUID id) {
        requireRequirementPermission("asset:read", id);
        return service.getRequirementIncludingInactive(id);
    }

    @PutMapping("/requirements/{id}")
    public RequirementResponse updateRequirement(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRequirementRequest request
    ) {
        requireRequirementPermission("asset:manage", id);
        return service.updateRequirement(id, request);
    }

    @PatchMapping("/requirements/{id}/lifecycle")
    public RequirementResponse updateRequirementLifecycle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetLifecycleRequest request
    ) {
        requireRequirementPermission("asset:manage", id);
        return service.updateRequirementLifecycle(id, request);
    }

    @GetMapping("/requirements/{id}/versions")
    public List<AssetVersionHistoryResponse> requirementVersions(@PathVariable UUID id) {
        requireRequirementPermission("asset:read", id);
        return service.requirementVersions(id);
    }

    @PostMapping("/requirements/{id}/versions/{version}/rollback")
    public RequirementResponse rollbackRequirementVersion(
            @PathVariable UUID id,
            @PathVariable int version,
            @Valid @RequestBody(required = false) RollbackAssetVersionRequest request
    ) {
        requireRequirementPermission("asset:manage", id);
        return service.rollbackRequirementVersion(id, version, request);
    }

    // ---- APIs ----

    @GetMapping("/apis")
    public com.songhg.veri.agent.common.api.PageResponse<ApiResponseDTO> listApis(
            @Valid AssetListRequest request
    ) {
        requireListPermission("asset:read", request);
        return service.listApis(request);
    }

    @PostMapping("/apis")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseDTO createApi(@Valid @RequestBody CreateApiRequest request) {
        requireProjectPermission("asset:manage", request.projectId());
        return service.createApi(request);
    }

    @GetMapping("/apis/{id}")
    public ApiResponseDTO getApi(@PathVariable UUID id) {
        requireApiPermission("asset:read", id);
        return service.getApi(id);
    }

    @GetMapping("/apis/{id}/lifecycle")
    public ApiResponseDTO getApiLifecycle(@PathVariable UUID id) {
        requireApiPermission("asset:read", id);
        return service.getApiIncludingInactive(id);
    }

    @PutMapping("/apis/{id}")
    public ApiResponseDTO updateApi(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApiRequest request
    ) {
        requireApiPermission("asset:manage", id);
        return service.updateApi(id, request);
    }

    @PatchMapping("/apis/{id}/lifecycle")
    public ApiResponseDTO updateApiLifecycle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetLifecycleRequest request
    ) {
        requireApiPermission("asset:manage", id);
        return service.updateApiLifecycle(id, request);
    }

    // ---- Pages ----

    @GetMapping("/pages")
    public com.songhg.veri.agent.common.api.PageResponse<PageResponse> listPages(
            @Valid AssetListRequest request
    ) {
        requireListPermission("asset:read", request);
        return service.listPages(request);
    }

    @PostMapping("/pages")
    @ResponseStatus(HttpStatus.CREATED)
    public PageResponse createPage(@Valid @RequestBody CreatePageRequest request) {
        requireProjectPermission("asset:manage", request.projectId());
        return service.createPage(request);
    }

    @GetMapping("/pages/{id}")
    public PageResponse getPage(@PathVariable UUID id) {
        requirePagePermission("asset:read", id);
        return service.getPage(id);
    }

    @GetMapping("/pages/{id}/lifecycle")
    public PageResponse getPageLifecycle(@PathVariable UUID id) {
        requirePagePermission("asset:read", id);
        return service.getPageIncludingInactive(id);
    }

    @PutMapping("/pages/{id}")
    public PageResponse updatePage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePageRequest request
    ) {
        requirePagePermission("asset:manage", id);
        return service.updatePage(id, request);
    }

    @PatchMapping("/pages/{id}/lifecycle")
    public PageResponse updatePageLifecycle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetLifecycleRequest request
    ) {
        requirePagePermission("asset:manage", id);
        return service.updatePageLifecycle(id, request);
    }

    // ---- Business Flows ----

    @GetMapping("/business-flows")
    public com.songhg.veri.agent.common.api.PageResponse<BusinessFlowResponse> listBusinessFlows(
            @Valid AssetListRequest request
    ) {
        requireListPermission("asset:read", request);
        return service.listBusinessFlows(request);
    }

    @PostMapping("/business-flows")
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessFlowResponse createBusinessFlow(@Valid @RequestBody CreateBusinessFlowRequest request) {
        requireProjectPermission("asset:manage", request.projectId());
        return service.createBusinessFlow(request);
    }

    @GetMapping("/business-flows/{id}")
    public BusinessFlowResponse getBusinessFlow(@PathVariable UUID id) {
        requireBusinessFlowPermission("asset:read", id);
        return service.getBusinessFlow(id);
    }

    @GetMapping("/business-flows/{id}/lifecycle")
    public BusinessFlowResponse getBusinessFlowLifecycle(@PathVariable UUID id) {
        requireBusinessFlowPermission("asset:read", id);
        return service.getBusinessFlowIncludingInactive(id);
    }

    @PutMapping("/business-flows/{id}")
    public BusinessFlowResponse updateBusinessFlow(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBusinessFlowRequest request
    ) {
        requireBusinessFlowPermission("asset:manage", id);
        return service.updateBusinessFlow(id, request);
    }

    @PatchMapping("/business-flows/{id}/lifecycle")
    public BusinessFlowResponse updateBusinessFlowLifecycle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetLifecycleRequest request
    ) {
        requireBusinessFlowPermission("asset:manage", id);
        return service.updateBusinessFlowLifecycle(id, request);
    }

    // ---- Test Cases ----

    @GetMapping("/test-cases")
    public com.songhg.veri.agent.common.api.PageResponse<TestCaseResponse> listTestCases(
            @Valid AssetListRequest request
    ) {
        requireListPermission("asset:read", request);
        return service.listTestCases(request);
    }

    @PostMapping("/test-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public TestCaseResponse createTestCase(@Valid @RequestBody CreateTestCaseRequest request) {
        requireProjectPermission("asset:manage", request.projectId());
        return service.createTestCase(request);
    }

    @GetMapping("/test-cases/{id}")
    public TestCaseResponse getTestCase(@PathVariable UUID id) {
        requireTestCasePermission("asset:read", id);
        return service.getTestCase(id);
    }

    @GetMapping("/test-cases/{id}/lifecycle")
    public TestCaseResponse getTestCaseLifecycle(@PathVariable UUID id) {
        requireTestCasePermission("asset:read", id);
        return service.getTestCaseIncludingInactive(id);
    }

    @PutMapping("/test-cases/{id}")
    public TestCaseResponse updateTestCase(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestCaseRequest request
    ) {
        requireTestCasePermission("asset:manage", id);
        return service.updateTestCase(id, request);
    }

    @PatchMapping("/test-cases/{id}/lifecycle")
    public TestCaseResponse updateTestCaseLifecycle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetLifecycleRequest request
    ) {
        requireTestCasePermission("asset:manage", id);
        return service.updateTestCaseLifecycle(id, request);
    }

    @GetMapping("/test-cases/{id}/versions")
    public List<AssetVersionHistoryResponse> testCaseVersions(@PathVariable UUID id) {
        requireTestCasePermission("asset:read", id);
        return service.testCaseVersions(id);
    }

    @PostMapping("/test-cases/{id}/versions/{version}/rollback")
    public TestCaseResponse rollbackTestCaseVersion(
            @PathVariable UUID id,
            @PathVariable int version,
            @Valid @RequestBody(required = false) RollbackAssetVersionRequest request
    ) {
        requireTestCasePermission("asset:manage", id);
        return service.rollbackTestCaseVersion(id, version, request);
    }

    @GetMapping("/test-cases/{id}/steps")
    public List<TestCaseStepResponse> listTestCaseSteps(@PathVariable UUID id) {
        requireTestCasePermission("asset:read", id);
        return service.listTestCaseSteps(id);
    }

    @PutMapping("/test-cases/{id}/steps")
    public List<TestCaseStepResponse> updateTestCaseSteps(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestCaseStepsRequest request
    ) {
        requireTestCasePermission("asset:manage", id);
        return service.updateTestCaseSteps(id, request);
    }

    // ---- Trace Links ----

    @GetMapping("/links")
    public com.songhg.veri.agent.common.api.PageResponse<TraceLinkResponse> listLinks(
            @Valid TraceLinkListRequest request
    ) {
        requireTraceLinkListPermission("asset:read", request);
        return service.listLinks(request);
    }

    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    public TraceLinkResponse createLink(@Valid @RequestBody CreateLinkRequest request) {
        requireRequirementPermission("asset:manage", request.requirementId());
        return service.createLink(request);
    }

    private AuthUserPrincipal requirePermission(String permission) {
        return authorizationService.requireCurrent(permission);
    }

    private void requireListPermission(String permission, AssetListRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            requireProjectPermission(permission, request.getProjectId());
            return;
        }
        requirePlatformPermission(permission);
    }

    private void requirePlatformPermission(String permission) {
        AuthUserPrincipal principal = requirePermission(permission);
        if (principal != null) {
            authorizationService.require(principal, permission, ResourceScope.platform());
        }
    }

    private void requireProjectPermission(String permission, String projectId) {
        AuthUserPrincipal principal = requirePermission(permission);
        if (principal != null) {
            authorizationService.require(principal, permission, ResourceScope.project(service.resolveProjectScopeId(projectId)));
        }
    }

    private void requireRequirementPermission(String permission, UUID id) {
        AuthUserPrincipal principal = requirePermission(permission);
        if (principal != null) {
            authorizationService.require(principal, permission, ResourceScope.project(service.requirementProjectScopeId(id)));
        }
    }

    private void requireApiPermission(String permission, UUID id) {
        AuthUserPrincipal principal = requirePermission(permission);
        if (principal != null) {
            authorizationService.require(principal, permission, ResourceScope.project(service.apiProjectScopeId(id)));
        }
    }

    private void requirePagePermission(String permission, UUID id) {
        AuthUserPrincipal principal = requirePermission(permission);
        if (principal != null) {
            authorizationService.require(principal, permission, ResourceScope.project(service.pageProjectScopeId(id)));
        }
    }

    private void requireBusinessFlowPermission(String permission, UUID id) {
        AuthUserPrincipal principal = requirePermission(permission);
        if (principal != null) {
            authorizationService.require(principal, permission, ResourceScope.project(service.businessFlowProjectScopeId(id)));
        }
    }

    private void requireTestCasePermission(String permission, UUID id) {
        AuthUserPrincipal principal = requirePermission(permission);
        if (principal != null) {
            authorizationService.require(principal, permission, ResourceScope.project(service.testCaseProjectScopeId(id)));
        }
    }

    private void requireTraceLinkListPermission(String permission, TraceLinkListRequest request) {
        if (request == null || (
                request.getRequirementId() == null
                        && request.getApiId() == null
                        && request.getPageId() == null
                        && request.getFlowId() == null
                        && request.getCaseId() == null
        )) {
            requirePlatformPermission(permission);
            return;
        }
        if (request.getRequirementId() != null) {
            requireRequirementPermission(permission, request.getRequirementId());
        }
        if (request.getApiId() != null) {
            requireApiPermission(permission, request.getApiId());
        }
        if (request.getPageId() != null) {
            requirePagePermission(permission, request.getPageId());
        }
        if (request.getFlowId() != null) {
            requireBusinessFlowPermission(permission, request.getFlowId());
        }
        if (request.getCaseId() != null) {
            requireTestCasePermission(permission, request.getCaseId());
        }
    }
}
