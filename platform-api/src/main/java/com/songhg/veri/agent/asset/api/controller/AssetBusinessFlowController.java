package com.songhg.veri.agent.asset.api.controller;

import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.command.CreateBusinessFlowRequest;
import com.songhg.veri.agent.asset.application.command.RollbackAssetVersionRequest;
import com.songhg.veri.agent.asset.application.command.UpdateAssetLifecycleRequest;
import com.songhg.veri.agent.asset.application.command.UpdateBusinessFlowRequest;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.view.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.application.view.BusinessFlowResponse;
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
@RequestMapping("/api/v1/asset/business-flows")
public class AssetBusinessFlowController {

    private final AssetService service;

    public AssetBusinessFlowController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.ASSET_LIST)
    public PageResponse<BusinessFlowResponse> listBusinessFlows(@Valid AssetListRequest request) {
        return service.listBusinessFlows(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.PROJECT_REQUEST)
    public BusinessFlowResponse createBusinessFlow(@Valid @RequestBody CreateBusinessFlowRequest request) {
        return service.createBusinessFlow(request);
    }

    @GetMapping("/{id}")
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.BUSINESS_FLOW)
    public BusinessFlowResponse getBusinessFlow(@PathVariable UUID id) {
        return service.getBusinessFlow(id);
    }

    @GetMapping("/{id}/lifecycle")
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.BUSINESS_FLOW)
    public BusinessFlowResponse getBusinessFlowLifecycle(@PathVariable UUID id) {
        return service.getBusinessFlowIncludingInactive(id);
    }

    @PutMapping("/{id}")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.BUSINESS_FLOW)
    public BusinessFlowResponse updateBusinessFlow(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBusinessFlowRequest request
    ) {
        return service.updateBusinessFlow(id, request);
    }

    @GetMapping("/{id}/versions")
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.BUSINESS_FLOW)
    public List<AssetVersionHistoryResponse> businessFlowVersions(@PathVariable UUID id) {
        return service.businessFlowVersions(id);
    }

    @PostMapping("/{id}/versions/{version}/rollback")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.BUSINESS_FLOW)
    public BusinessFlowResponse rollbackBusinessFlowVersion(
            @PathVariable UUID id,
            @PathVariable int version,
            @Valid @RequestBody(required = false) RollbackAssetVersionRequest request
    ) {
        return service.rollbackBusinessFlowVersion(id, version, request);
    }

    @PatchMapping("/{id}/lifecycle")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.BUSINESS_FLOW)
    public BusinessFlowResponse updateBusinessFlowLifecycle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetLifecycleRequest request
    ) {
        return service.updateBusinessFlowLifecycle(id, request);
    }
}
