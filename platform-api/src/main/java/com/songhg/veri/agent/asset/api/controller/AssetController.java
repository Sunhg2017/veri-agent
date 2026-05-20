package com.songhg.veri.agent.asset.api.controller;

import com.songhg.veri.agent.asset.api.request.AssetListRequest;
import com.songhg.veri.agent.asset.api.request.CreateApiRequest;
import com.songhg.veri.agent.asset.api.request.CreateBusinessFlowRequest;
import com.songhg.veri.agent.asset.api.request.CreateLinkRequest;
import com.songhg.veri.agent.asset.api.request.CreatePageRequest;
import com.songhg.veri.agent.asset.api.request.CreateRequirementRequest;
import com.songhg.veri.agent.asset.api.request.CreateTestCaseRequest;
import com.songhg.veri.agent.asset.api.request.TraceLinkListRequest;
import com.songhg.veri.agent.asset.api.request.UpdateApiRequest;
import com.songhg.veri.agent.asset.api.request.UpdateBusinessFlowRequest;
import com.songhg.veri.agent.asset.api.request.UpdatePageRequest;
import com.songhg.veri.agent.asset.api.request.UpdateRequirementRequest;
import com.songhg.veri.agent.asset.api.request.UpdateTestCaseRequest;
import com.songhg.veri.agent.asset.api.request.UpdateTestCaseStepsRequest;
import com.songhg.veri.agent.asset.api.response.ApiResponseDTO;
import com.songhg.veri.agent.asset.api.response.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.api.response.BusinessFlowResponse;
import com.songhg.veri.agent.asset.api.response.PageResponse;
import com.songhg.veri.agent.asset.api.response.RequirementResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseStepResponse;
import com.songhg.veri.agent.asset.api.response.TraceLinkResponse;
import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/asset")
public class AssetController {

    private final AssetService service;
    private final AuthorizationService authorizationService;

    public AssetController(AssetService service, AuthorizationService authorizationService) {
        this.service = service;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("service", "asset-service", "status", service.health());
    }

    // ---- Requirements ----

    @GetMapping("/requirements")
    public com.songhg.veri.agent.common.api.PageResponse<RequirementResponse> listRequirements(
            @Valid AssetListRequest request
    ) {
        requirePermission("asset:read");
        return service.listRequirements(request);
    }

    @PostMapping("/requirements")
    @ResponseStatus(HttpStatus.CREATED)
    public RequirementResponse createRequirement(@Valid @RequestBody CreateRequirementRequest request) {
        requirePermission("asset:manage");
        return service.createRequirement(request);
    }

    @GetMapping("/requirements/{id}")
    public RequirementResponse getRequirement(@PathVariable UUID id) {
        requirePermission("asset:read");
        return service.getRequirement(id);
    }

    @PutMapping("/requirements/{id}")
    public RequirementResponse updateRequirement(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRequirementRequest request
    ) {
        requirePermission("asset:manage");
        return service.updateRequirement(id, request);
    }

    @GetMapping("/requirements/{id}/versions")
    public List<AssetVersionHistoryResponse> requirementVersions(@PathVariable UUID id) {
        requirePermission("asset:read");
        return service.requirementVersions(id);
    }

    // ---- APIs ----

    @GetMapping("/apis")
    public com.songhg.veri.agent.common.api.PageResponse<ApiResponseDTO> listApis(
            @Valid AssetListRequest request
    ) {
        requirePermission("asset:read");
        return service.listApis(request);
    }

    @PostMapping("/apis")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseDTO createApi(@Valid @RequestBody CreateApiRequest request) {
        requirePermission("asset:manage");
        return service.createApi(request);
    }

    @GetMapping("/apis/{id}")
    public ApiResponseDTO getApi(@PathVariable UUID id) {
        requirePermission("asset:read");
        return service.getApi(id);
    }

    @PutMapping("/apis/{id}")
    public ApiResponseDTO updateApi(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApiRequest request
    ) {
        requirePermission("asset:manage");
        return service.updateApi(id, request);
    }

    // ---- Pages ----

    @GetMapping("/pages")
    public com.songhg.veri.agent.common.api.PageResponse<PageResponse> listPages(
            @Valid AssetListRequest request
    ) {
        requirePermission("asset:read");
        return service.listPages(request);
    }

    @PostMapping("/pages")
    @ResponseStatus(HttpStatus.CREATED)
    public PageResponse createPage(@Valid @RequestBody CreatePageRequest request) {
        requirePermission("asset:manage");
        return service.createPage(request);
    }

    @GetMapping("/pages/{id}")
    public PageResponse getPage(@PathVariable UUID id) {
        requirePermission("asset:read");
        return service.getPage(id);
    }

    @PutMapping("/pages/{id}")
    public PageResponse updatePage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePageRequest request
    ) {
        requirePermission("asset:manage");
        return service.updatePage(id, request);
    }

    // ---- Business Flows ----

    @GetMapping("/business-flows")
    public com.songhg.veri.agent.common.api.PageResponse<BusinessFlowResponse> listBusinessFlows(
            @Valid AssetListRequest request
    ) {
        requirePermission("asset:read");
        return service.listBusinessFlows(request);
    }

    @PostMapping("/business-flows")
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessFlowResponse createBusinessFlow(@Valid @RequestBody CreateBusinessFlowRequest request) {
        requirePermission("asset:manage");
        return service.createBusinessFlow(request);
    }

    @GetMapping("/business-flows/{id}")
    public BusinessFlowResponse getBusinessFlow(@PathVariable UUID id) {
        requirePermission("asset:read");
        return service.getBusinessFlow(id);
    }

    @PutMapping("/business-flows/{id}")
    public BusinessFlowResponse updateBusinessFlow(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBusinessFlowRequest request
    ) {
        requirePermission("asset:manage");
        return service.updateBusinessFlow(id, request);
    }

    // ---- Test Cases ----

    @GetMapping("/test-cases")
    public com.songhg.veri.agent.common.api.PageResponse<TestCaseResponse> listTestCases(
            @Valid AssetListRequest request
    ) {
        requirePermission("asset:read");
        return service.listTestCases(request);
    }

    @PostMapping("/test-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public TestCaseResponse createTestCase(@Valid @RequestBody CreateTestCaseRequest request) {
        requirePermission("asset:manage");
        return service.createTestCase(request);
    }

    @GetMapping("/test-cases/{id}")
    public TestCaseResponse getTestCase(@PathVariable UUID id) {
        requirePermission("asset:read");
        return service.getTestCase(id);
    }

    @PutMapping("/test-cases/{id}")
    public TestCaseResponse updateTestCase(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestCaseRequest request
    ) {
        requirePermission("asset:manage");
        return service.updateTestCase(id, request);
    }

    @GetMapping("/test-cases/{id}/versions")
    public List<AssetVersionHistoryResponse> testCaseVersions(@PathVariable UUID id) {
        requirePermission("asset:read");
        return service.testCaseVersions(id);
    }

    @GetMapping("/test-cases/{id}/steps")
    public List<TestCaseStepResponse> listTestCaseSteps(@PathVariable UUID id) {
        requirePermission("asset:read");
        return service.listTestCaseSteps(id);
    }

    @PutMapping("/test-cases/{id}/steps")
    public List<TestCaseStepResponse> updateTestCaseSteps(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestCaseStepsRequest request
    ) {
        requirePermission("asset:manage");
        return service.updateTestCaseSteps(id, request);
    }

    // ---- Trace Links ----

    @GetMapping("/links")
    public com.songhg.veri.agent.common.api.PageResponse<TraceLinkResponse> listLinks(
            @Valid TraceLinkListRequest request
    ) {
        requirePermission("asset:read");
        return service.listLinks(request);
    }

    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    public TraceLinkResponse createLink(@Valid @RequestBody CreateLinkRequest request) {
        requirePermission("asset:manage");
        return service.createLink(request);
    }

    private void requirePermission(String permission) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ServicePrincipal) {
            return;
        }
        if (authentication != null && authentication.getPrincipal() instanceof AuthUserPrincipal principal) {
            authorizationService.require(principal, permission);
            return;
        }
        throw new AccessDeniedException("缺少权限：" + permission);
    }
}
