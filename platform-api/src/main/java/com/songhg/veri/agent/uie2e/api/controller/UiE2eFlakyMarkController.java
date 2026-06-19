package com.songhg.veri.agent.uie2e.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.uie2e.application.UiE2eFlakyMarkService;
import com.songhg.veri.agent.uie2e.application.command.UpsertUiE2eFlakyMarkCommand;
import com.songhg.veri.agent.uie2e.application.query.UiE2eFlakyMarkPageRequest;
import com.songhg.veri.agent.uie2e.application.view.UiE2eFlakyMarkResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/ui-e2e/flaky-marks")
public class UiE2eFlakyMarkController {

    private final UiE2eFlakyMarkService service;

    public UiE2eFlakyMarkController(UiE2eFlakyMarkService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(value = PermissionCodes.UI_E2E_FLAKY, scope = UiE2ePermissionScopes.FLAKY_REQUEST)
    public UiE2eFlakyMarkResponse upsert(@Valid @RequestBody UpsertUiE2eFlakyMarkCommand command) {
        return service.upsert(command);
    }

    @GetMapping("/{id}")
    @RequirePermission(value = PermissionCodes.UI_E2E_READ, scope = UiE2ePermissionScopes.FLAKY)
    public UiE2eFlakyMarkResponse flakyMark(@PathVariable UUID id) {
        return service.flakyMark(id);
    }

    @GetMapping
    @RequirePermission(value = PermissionCodes.UI_E2E_READ, scope = UiE2ePermissionScopes.FLAKY_LIST)
    public PageResponse<UiE2eFlakyMarkResponse> flakyMarks(@Valid UiE2eFlakyMarkPageRequest request) {
        return service.flakyMarks(request);
    }
}
