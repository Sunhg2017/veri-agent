package com.songhg.veri.agent.asset.api.controller;

import com.songhg.veri.agent.asset.api.request.CreateApiRequest;
import com.songhg.veri.agent.asset.api.request.CreateBusinessFlowRequest;
import com.songhg.veri.agent.asset.api.request.CreateLinkRequest;
import com.songhg.veri.agent.asset.api.request.CreatePageRequest;
import com.songhg.veri.agent.asset.api.request.CreateRequirementRequest;
import com.songhg.veri.agent.asset.api.request.CreateTestCaseRequest;
import com.songhg.veri.agent.asset.api.request.UpdateApiRequest;
import com.songhg.veri.agent.asset.api.request.UpdateBusinessFlowRequest;
import com.songhg.veri.agent.asset.api.request.UpdatePageRequest;
import com.songhg.veri.agent.asset.api.request.UpdateRequirementRequest;
import com.songhg.veri.agent.asset.api.request.UpdateTestCaseRequest;
import com.songhg.veri.agent.asset.api.request.UpdateTestCaseStepsRequest;
import com.songhg.veri.agent.asset.api.response.ApiResponseDTO;
import com.songhg.veri.agent.asset.api.response.BusinessFlowResponse;
import com.songhg.veri.agent.asset.api.response.PageResponse;
import com.songhg.veri.agent.asset.api.response.RequirementResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseStepResponse;
import com.songhg.veri.agent.asset.api.response.TraceLinkResponse;
import com.songhg.veri.agent.asset.application.AssetService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/asset")
public class AssetController {

    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("service", "asset-service", "status", service.health());
    }

    // ---- Requirements ----

    @GetMapping("/requirements")
    public List<RequirementResponse> listRequirements(
            @RequestParam(required = false) String projectId
    ) {
        return service.listRequirements(projectId);
    }

    @PostMapping("/requirements")
    @ResponseStatus(HttpStatus.CREATED)
    public RequirementResponse createRequirement(@Valid @RequestBody CreateRequirementRequest request) {
        return service.createRequirement(request);
    }

    @GetMapping("/requirements/{id}")
    public RequirementResponse getRequirement(@PathVariable UUID id) {
        return service.getRequirement(id);
    }

    @PutMapping("/requirements/{id}")
    public RequirementResponse updateRequirement(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRequirementRequest request
    ) {
        return service.updateRequirement(id, request);
    }

    // ---- APIs ----

    @GetMapping("/apis")
    public List<ApiResponseDTO> listApis(
            @RequestParam(required = false) String projectId
    ) {
        return service.listApis(projectId);
    }

    @PostMapping("/apis")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseDTO createApi(@Valid @RequestBody CreateApiRequest request) {
        return service.createApi(request);
    }

    @GetMapping("/apis/{id}")
    public ApiResponseDTO getApi(@PathVariable UUID id) {
        return service.getApi(id);
    }

    @PutMapping("/apis/{id}")
    public ApiResponseDTO updateApi(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApiRequest request
    ) {
        return service.updateApi(id, request);
    }

    // ---- Pages ----

    @GetMapping("/pages")
    public List<PageResponse> listPages(
            @RequestParam(required = false) String projectId
    ) {
        return service.listPages(projectId);
    }

    @PostMapping("/pages")
    @ResponseStatus(HttpStatus.CREATED)
    public PageResponse createPage(@Valid @RequestBody CreatePageRequest request) {
        return service.createPage(request);
    }

    @GetMapping("/pages/{id}")
    public PageResponse getPage(@PathVariable UUID id) {
        return service.getPage(id);
    }

    @PutMapping("/pages/{id}")
    public PageResponse updatePage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePageRequest request
    ) {
        return service.updatePage(id, request);
    }

    // ---- Business Flows ----

    @GetMapping("/business-flows")
    public List<BusinessFlowResponse> listBusinessFlows(
            @RequestParam(required = false) String projectId
    ) {
        return service.listBusinessFlows(projectId);
    }

    @PostMapping("/business-flows")
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessFlowResponse createBusinessFlow(@Valid @RequestBody CreateBusinessFlowRequest request) {
        return service.createBusinessFlow(request);
    }

    @GetMapping("/business-flows/{id}")
    public BusinessFlowResponse getBusinessFlow(@PathVariable UUID id) {
        return service.getBusinessFlow(id);
    }

    @PutMapping("/business-flows/{id}")
    public BusinessFlowResponse updateBusinessFlow(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBusinessFlowRequest request
    ) {
        return service.updateBusinessFlow(id, request);
    }

    // ---- Test Cases ----

    @GetMapping("/test-cases")
    public List<TestCaseResponse> listTestCases(
            @RequestParam(required = false) String projectId
    ) {
        return service.listTestCases(projectId);
    }

    @PostMapping("/test-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public TestCaseResponse createTestCase(@Valid @RequestBody CreateTestCaseRequest request) {
        return service.createTestCase(request);
    }

    @GetMapping("/test-cases/{id}")
    public TestCaseResponse getTestCase(@PathVariable UUID id) {
        return service.getTestCase(id);
    }

    @PutMapping("/test-cases/{id}")
    public TestCaseResponse updateTestCase(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestCaseRequest request
    ) {
        return service.updateTestCase(id, request);
    }

    @GetMapping("/test-cases/{id}/steps")
    public List<TestCaseStepResponse> listTestCaseSteps(@PathVariable UUID id) {
        return service.listTestCaseSteps(id);
    }

    @PutMapping("/test-cases/{id}/steps")
    public List<TestCaseStepResponse> updateTestCaseSteps(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestCaseStepsRequest request
    ) {
        return service.updateTestCaseSteps(id, request);
    }

    // ---- Trace Links ----

    @GetMapping("/links")
    public List<TraceLinkResponse> listLinks(
            @RequestParam(required = false) UUID requirementId,
            @RequestParam(required = false) UUID apiId,
            @RequestParam(required = false) UUID caseId
    ) {
        return service.listLinks(requirementId, apiId, caseId);
    }

    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    public TraceLinkResponse createLink(@Valid @RequestBody CreateLinkRequest request) {
        return service.createLink(request);
    }
}
