package com.songhg.veri.agent.uie2e.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.uie2e.application.UiE2eSceneImportService;
import com.songhg.veri.agent.uie2e.application.UiE2eSceneService;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eSceneCommand;
import com.songhg.veri.agent.uie2e.application.command.ImportUiE2eSceneCommand;
import com.songhg.veri.agent.uie2e.application.command.UpdateUiE2eSceneCommand;
import com.songhg.veri.agent.uie2e.application.query.UiE2eScenePageRequest;
import com.songhg.veri.agent.uie2e.application.view.UiE2eSceneDetailResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eSceneImportResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eSceneSummaryResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/ui-e2e/scenes")
public class UiE2eSceneController {

    private final UiE2eSceneService service;
    private final UiE2eSceneImportService importService;

    public UiE2eSceneController(UiE2eSceneService service, UiE2eSceneImportService importService) {
        this.service = service;
        this.importService = importService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.UI_E2E_MANAGE, scope = UiE2ePermissionScopes.SCENE_REQUEST)
    public UiE2eSceneDetailResponse createScene(@Valid @RequestBody CreateUiE2eSceneCommand command) {
        return service.createScene(command);
    }

    @PostMapping("/import")
    @RequirePermission(value = PermissionCodes.UI_E2E_MANAGE, scope = UiE2ePermissionScopes.SCENE_IMPORT_REQUEST)
    public UiE2eSceneImportResponse importScene(@Valid @RequestBody ImportUiE2eSceneCommand command) {
        return importService.importScene(command);
    }

    @GetMapping
    @RequirePermission(value = PermissionCodes.UI_E2E_READ, scope = UiE2ePermissionScopes.SCENE_LIST)
    public PageResponse<UiE2eSceneSummaryResponse> scenes(@Valid UiE2eScenePageRequest request) {
        return service.scenes(request);
    }

    @GetMapping("/{id}")
    @RequirePermission(value = PermissionCodes.UI_E2E_READ, scope = UiE2ePermissionScopes.SCENE)
    public UiE2eSceneDetailResponse scene(@PathVariable UUID id) {
        return service.scene(id);
    }

    @PatchMapping("/{id}")
    @RequirePermission(value = PermissionCodes.UI_E2E_MANAGE, scope = UiE2ePermissionScopes.SCENE)
    public UiE2eSceneDetailResponse updateScene(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUiE2eSceneCommand command
    ) {
        return service.updateScene(id, command);
    }

    @PostMapping("/{id}/archive")
    @RequirePermission(value = PermissionCodes.UI_E2E_MANAGE, scope = UiE2ePermissionScopes.SCENE)
    public UiE2eSceneDetailResponse archiveScene(@PathVariable UUID id) {
        return service.archiveScene(id);
    }
}
