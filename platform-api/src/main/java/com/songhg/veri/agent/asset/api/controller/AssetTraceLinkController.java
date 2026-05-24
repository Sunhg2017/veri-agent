package com.songhg.veri.agent.asset.api.controller;

import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.command.CreateLinkRequest;
import com.songhg.veri.agent.asset.application.query.TraceLinkListRequest;
import com.songhg.veri.agent.asset.application.view.TraceLinkResponse;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/asset/links")
public class AssetTraceLinkController {

    private final AssetService service;

    public AssetTraceLinkController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.TRACE_LINK_LIST)
    public PageResponse<TraceLinkResponse> listLinks(@Valid TraceLinkListRequest request) {
        return service.listLinks(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.CREATE_LINK)
    public TraceLinkResponse createLink(@Valid @RequestBody CreateLinkRequest request) {
        return service.createLink(request);
    }
}
