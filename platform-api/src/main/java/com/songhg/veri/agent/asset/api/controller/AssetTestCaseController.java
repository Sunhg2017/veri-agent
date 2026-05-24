package com.songhg.veri.agent.asset.api.controller;

import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.command.CreateTestCaseRequest;
import com.songhg.veri.agent.asset.application.command.RollbackAssetVersionRequest;
import com.songhg.veri.agent.asset.application.command.UpdateAssetLifecycleRequest;
import com.songhg.veri.agent.asset.application.command.UpdateTestCaseRequest;
import com.songhg.veri.agent.asset.application.command.UpdateTestCaseStepsRequest;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.view.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseStepResponse;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/asset/test-cases")
public class AssetTestCaseController {

    private final AssetService service;

    public AssetTestCaseController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.ASSET_LIST)
    public PageResponse<TestCaseResponse> listTestCases(@Valid AssetListRequest request) {
        return service.listTestCases(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.PROJECT_REQUEST)
    public TestCaseResponse createTestCase(@Valid @RequestBody CreateTestCaseRequest request) {
        return service.createTestCase(request);
    }

    @GetMapping("/{id}")
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.TEST_CASE)
    public TestCaseResponse getTestCase(@PathVariable UUID id) {
        return service.getTestCase(id);
    }

    @GetMapping("/{id}/lifecycle")
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.TEST_CASE)
    public TestCaseResponse getTestCaseLifecycle(@PathVariable UUID id) {
        return service.getTestCaseIncludingInactive(id);
    }

    @PutMapping("/{id}")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.TEST_CASE)
    public TestCaseResponse updateTestCase(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestCaseRequest request
    ) {
        return service.updateTestCase(id, request);
    }

    @PatchMapping("/{id}/lifecycle")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.TEST_CASE)
    public TestCaseResponse updateTestCaseLifecycle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetLifecycleRequest request
    ) {
        return service.updateTestCaseLifecycle(id, request);
    }

    @GetMapping("/{id}/versions")
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.TEST_CASE)
    public List<AssetVersionHistoryResponse> testCaseVersions(@PathVariable UUID id) {
        return service.testCaseVersions(id);
    }

    @PostMapping("/{id}/versions/{version}/rollback")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.TEST_CASE)
    public TestCaseResponse rollbackTestCaseVersion(
            @PathVariable UUID id,
            @PathVariable int version,
            @Valid @RequestBody(required = false) RollbackAssetVersionRequest request
    ) {
        return service.rollbackTestCaseVersion(id, version, request);
    }

    @GetMapping("/{id}/steps")
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.TEST_CASE)
    public List<TestCaseStepResponse> listTestCaseSteps(@PathVariable UUID id) {
        return service.listTestCaseSteps(id);
    }

    @PutMapping("/{id}/steps")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.TEST_CASE)
    public List<TestCaseStepResponse> updateTestCaseSteps(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestCaseStepsRequest request
    ) {
        return service.updateTestCaseSteps(id, request);
    }
}
