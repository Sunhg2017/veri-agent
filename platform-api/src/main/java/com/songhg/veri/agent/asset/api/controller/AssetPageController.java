package com.songhg.veri.agent.asset.api.controller;

import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.command.CreatePageRequest;
import com.songhg.veri.agent.asset.application.command.UpdateAssetLifecycleRequest;
import com.songhg.veri.agent.asset.application.command.UpdatePageRequest;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.view.PageResponse;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/asset/pages")
public class AssetPageController {

    private final AssetService service;

    public AssetPageController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.ASSET_LIST)
    public com.songhg.veri.agent.common.api.PageResponse<PageResponse> listPages(@Valid AssetListRequest request) {
        return service.listPages(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.PROJECT_REQUEST)
    public PageResponse createPage(@Valid @RequestBody CreatePageRequest request) {
        return service.createPage(request);
    }

    @GetMapping("/{id}")
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.PAGE)
    public PageResponse getPage(@PathVariable UUID id) {
        return service.getPage(id);
    }

    @GetMapping("/{id}/lifecycle")
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.PAGE)
    public PageResponse getPageLifecycle(@PathVariable UUID id) {
        return service.getPageIncludingInactive(id);
    }

    @PutMapping("/{id}")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.PAGE)
    public PageResponse updatePage(@PathVariable UUID id, @Valid @RequestBody UpdatePageRequest request) {
        return service.updatePage(id, request);
    }

    @PatchMapping("/{id}/lifecycle")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.PAGE)
    public PageResponse updatePageLifecycle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetLifecycleRequest request
    ) {
        return service.updatePageLifecycle(id, request);
    }
}
