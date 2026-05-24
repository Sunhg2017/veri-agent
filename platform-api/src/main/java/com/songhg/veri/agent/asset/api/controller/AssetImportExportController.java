package com.songhg.veri.agent.asset.api.controller;

import com.songhg.veri.agent.asset.application.AssetImportExportService;
import com.songhg.veri.agent.asset.application.AssetPrototypeSyncService;
import com.songhg.veri.agent.asset.application.command.AssetImportRequest;
import com.songhg.veri.agent.asset.application.command.AssetPrototypeSyncRequest;
import com.songhg.veri.agent.asset.application.query.AssetExportRequest;
import com.songhg.veri.agent.asset.application.view.AssetExportPayload;
import com.songhg.veri.agent.asset.application.view.AssetImportResponse;
import com.songhg.veri.agent.asset.application.view.AssetPrototypeSyncResponse;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/asset")
public class AssetImportExportController {

    private final AssetImportExportService importExportService;
    private final AssetPrototypeSyncService prototypeSyncService;

    public AssetImportExportController(
            AssetImportExportService importExportService,
            AssetPrototypeSyncService prototypeSyncService
    ) {
        this.importExportService = importExportService;
        this.prototypeSyncService = prototypeSyncService;
    }

    @PostMapping("/imports")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.PROJECT_REQUEST)
    public AssetImportResponse importAssets(@Valid @RequestBody AssetImportRequest request) {
        return importExportService.importAssets(request);
    }

    @GetMapping("/exports")
    @RequirePermission(value = PermissionCodes.ASSET_EXPORT, scope = AssetPermissionScopes.ASSET_LIST)
    public ResponseEntity<byte[]> exportAssets(@Valid AssetExportRequest request) {
        AssetExportPayload payload = importExportService.exportAssets(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.fileName() + "\"")
                .body(payload.content());
    }

    @PostMapping("/prototype-sync")
    @RequirePermission(value = PermissionCodes.ASSET_MANAGE, scope = AssetPermissionScopes.PROJECT_REQUEST)
    public AssetPrototypeSyncResponse syncPrototypePages(@Valid @RequestBody AssetPrototypeSyncRequest request) {
        return prototypeSyncService.syncPrototypePages(request);
    }
}
