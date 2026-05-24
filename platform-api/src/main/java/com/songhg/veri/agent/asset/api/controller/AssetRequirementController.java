package com.songhg.veri.agent.asset.api.controller;

import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.command.CreateRequirementRequest;
import com.songhg.veri.agent.asset.application.command.RollbackAssetVersionRequest;
import com.songhg.veri.agent.asset.application.command.UpdateAssetLifecycleRequest;
import com.songhg.veri.agent.asset.application.command.UpdateRequirementRequest;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.view.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
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
@RequestMapping("/api/v1/asset/requirements")
public class AssetRequirementController {

    private final AssetService service;

    public AssetRequirementController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.ASSET_LIST)
    public PageResponse<RequirementResponse> listRequirements(@Valid AssetListRequest request) {
        return service.listRequirements(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.PROJECT_REQUEST)
    public RequirementResponse createRequirement(@Valid @RequestBody CreateRequirementRequest request) {
        return service.createRequirement(request);
    }

    @GetMapping("/{id}")
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.REQUIREMENT)
    public RequirementResponse getRequirement(@PathVariable UUID id) {
        return service.getRequirement(id);
    }

    @GetMapping("/{id}/lifecycle")
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.REQUIREMENT)
    public RequirementResponse getRequirementLifecycle(@PathVariable UUID id) {
        return service.getRequirementIncludingInactive(id);
    }

    @PutMapping("/{id}")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.REQUIREMENT)
    public RequirementResponse updateRequirement(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRequirementRequest request
    ) {
        return service.updateRequirement(id, request);
    }

    @PatchMapping("/{id}/lifecycle")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.REQUIREMENT)
    public RequirementResponse updateRequirementLifecycle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetLifecycleRequest request
    ) {
        return service.updateRequirementLifecycle(id, request);
    }

    @GetMapping("/{id}/versions")
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.REQUIREMENT)
    public List<AssetVersionHistoryResponse> requirementVersions(@PathVariable UUID id) {
        return service.requirementVersions(id);
    }

    @PostMapping("/{id}/versions/{version}/rollback")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.REQUIREMENT)
    public RequirementResponse rollbackRequirementVersion(
            @PathVariable UUID id,
            @PathVariable int version,
            @Valid @RequestBody(required = false) RollbackAssetVersionRequest request
    ) {
        return service.rollbackRequirementVersion(id, version, request);
    }
}
