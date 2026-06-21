package com.songhg.veri.agent.asset.api.controller;

import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.command.CreateApiRequest;
import com.songhg.veri.agent.asset.application.command.RollbackAssetVersionRequest;
import com.songhg.veri.agent.asset.application.command.UpdateApiRequest;
import com.songhg.veri.agent.asset.application.command.UpdateAssetLifecycleRequest;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.view.ApiResponseDTO;
import com.songhg.veri.agent.asset.application.view.AssetVersionHistoryResponse;
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
@RequestMapping("/api/v1/asset/apis")
public class AssetApiController {

    private final AssetService service;

    public AssetApiController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.ASSET_LIST)
    public PageResponse<ApiResponseDTO> listApis(@Valid AssetListRequest request) {
        return service.listApis(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.PROJECT_REQUEST)
    public ApiResponseDTO createApi(@Valid @RequestBody CreateApiRequest request) {
        return service.createApi(request);
    }

    @GetMapping("/{id}")
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.API)
    public ApiResponseDTO getApi(@PathVariable UUID id) {
        return service.getApi(id);
    }

    @GetMapping("/{id}/lifecycle")
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.API)
    public ApiResponseDTO getApiLifecycle(@PathVariable UUID id) {
        return service.getApiIncludingInactive(id);
    }

    @PutMapping("/{id}")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.API)
    public ApiResponseDTO updateApi(@PathVariable UUID id, @Valid @RequestBody UpdateApiRequest request) {
        return service.updateApi(id, request);
    }

    @GetMapping("/{id}/versions")
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.API)
    public List<AssetVersionHistoryResponse> apiVersions(@PathVariable UUID id) {
        return service.apiVersions(id);
    }

    @PostMapping("/{id}/versions/{version}/rollback")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.API)
    public ApiResponseDTO rollbackApiVersion(
            @PathVariable UUID id,
            @PathVariable int version,
            @Valid @RequestBody(required = false) RollbackAssetVersionRequest request
    ) {
        return service.rollbackApiVersion(id, version, request);
    }

    @PatchMapping("/{id}/lifecycle")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.API)
    public ApiResponseDTO updateApiLifecycle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetLifecycleRequest request
    ) {
        return service.updateApiLifecycle(id, request);
    }
}
