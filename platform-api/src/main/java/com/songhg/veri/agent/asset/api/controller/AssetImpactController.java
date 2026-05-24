package com.songhg.veri.agent.asset.api.controller;

import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.view.AssetImpactAnalysisResponse;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/asset")
public class AssetImpactController {

    private final AssetService service;

    public AssetImpactController(AssetService service) {
        this.service = service;
    }

    @GetMapping("/impact")
    @RequirePermission(value = PermissionCodes.ASSET_READ, scope = AssetPermissionScopes.PROJECT_QUERY)
    public AssetImpactAnalysisResponse analyzeImpact(
            @RequestParam String projectId,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) UUID assetId
    ) {
        return service.analyzeImpact(projectId, assetType, assetId);
    }
}
