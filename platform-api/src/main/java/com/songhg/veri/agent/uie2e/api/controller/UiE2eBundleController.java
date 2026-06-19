package com.songhg.veri.agent.uie2e.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.uie2e.application.UiE2eBundleService;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eBundleCommand;
import com.songhg.veri.agent.uie2e.application.command.ReviewUiE2eBundleCommand;
import com.songhg.veri.agent.uie2e.application.query.UiE2eBundlePageRequest;
import com.songhg.veri.agent.uie2e.application.view.UiE2eBundleDetailResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eBundleExportResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eBundleSummaryResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/ui-e2e/bundles")
public class UiE2eBundleController {

    private final UiE2eBundleService service;

    public UiE2eBundleController(UiE2eBundleService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.UI_E2E_MANAGE, scope = UiE2ePermissionScopes.BUNDLE_REQUEST)
    public UiE2eBundleDetailResponse createBundle(@Valid @RequestBody CreateUiE2eBundleCommand command) {
        return service.createOrRefreshBundle(command);
    }

    @GetMapping
    @RequirePermission(value = PermissionCodes.UI_E2E_READ, scope = UiE2ePermissionScopes.BUNDLE_LIST)
    public PageResponse<UiE2eBundleSummaryResponse> bundles(@Valid UiE2eBundlePageRequest request) {
        return service.bundles(request);
    }

    @GetMapping("/{id}")
    @RequirePermission(value = PermissionCodes.UI_E2E_READ, scope = UiE2ePermissionScopes.BUNDLE)
    public UiE2eBundleDetailResponse bundle(@PathVariable UUID id) {
        return service.bundle(id);
    }

    @GetMapping("/{id}/export")
    @RequirePermission(value = PermissionCodes.UI_E2E_EXPORT, scope = UiE2ePermissionScopes.BUNDLE)
    public UiE2eBundleExportResponse exportBundle(@PathVariable UUID id) {
        return service.exportBundle(id);
    }

    @PostMapping("/{id}/archive")
    @RequirePermission(value = PermissionCodes.UI_E2E_MANAGE, scope = UiE2ePermissionScopes.BUNDLE)
    public UiE2eBundleDetailResponse archiveBundle(@PathVariable UUID id) {
        return service.archiveBundle(id);
    }

    @PostMapping("/{id}/submit-review")
    @RequirePermission(value = PermissionCodes.UI_E2E_REVIEW, scope = UiE2ePermissionScopes.BUNDLE)
    public UiE2eBundleDetailResponse submitReview(
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewUiE2eBundleCommand command
    ) {
        return service.submitReview(id, command);
    }

    @PostMapping("/{id}/approve")
    @RequirePermission(value = PermissionCodes.UI_E2E_REVIEW, scope = UiE2ePermissionScopes.BUNDLE)
    public UiE2eBundleDetailResponse approve(
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewUiE2eBundleCommand command
    ) {
        return service.approve(id, command);
    }

    @PostMapping("/{id}/reject")
    @RequirePermission(value = PermissionCodes.UI_E2E_REVIEW, scope = UiE2ePermissionScopes.BUNDLE)
    public UiE2eBundleDetailResponse reject(
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewUiE2eBundleCommand command
    ) {
        return service.reject(id, command);
    }
}
