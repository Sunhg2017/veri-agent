package com.songhg.veri.agent.apiautomation.api.controller;

import com.songhg.veri.agent.apiautomation.application.ApiAutomationService;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationGenerationTaskCommand;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationRunCommand;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationSpecCommand;
import com.songhg.veri.agent.apiautomation.application.command.ReviewApiAutomationScriptBundleCommand;
import com.songhg.veri.agent.apiautomation.application.command.SyncApiAutomationSpecCommand;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecPageRequest;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationDiffResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationGenerationTaskDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationHealthResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationScriptBundleResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSyncResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSpecDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSpecResponse;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
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
@RequestMapping("/api/v1/api-automation")
public class ApiAutomationController {

    private final ApiAutomationService service;

    public ApiAutomationController(ApiAutomationService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public ApiAutomationHealthResponse health() {
        return service.health();
    }

    @PostMapping("/specs")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.API_AUTOMATION_IMPORT, scope = ApiAutomationPermissionScopes.SPEC_REQUEST)
    public ApiAutomationSpecDetailResponse createSpec(@Valid @RequestBody CreateApiAutomationSpecCommand command) {
        return service.createSpec(command);
    }

    @GetMapping("/specs")
    @RequirePermission(value = PermissionCodes.API_AUTOMATION_READ, scope = ApiAutomationPermissionScopes.SPEC_LIST)
    public PageResponse<ApiAutomationSpecResponse> specs(@Valid ApiAutomationSpecPageRequest request) {
        return service.specs(request);
    }

    @GetMapping("/specs/{id}")
    @RequirePermission(value = PermissionCodes.API_AUTOMATION_READ, scope = ApiAutomationPermissionScopes.SPEC)
    public ApiAutomationSpecDetailResponse spec(@PathVariable UUID id) {
        return service.specDetail(id);
    }

    @PostMapping("/specs/{id}/parse")
    @RequirePermission(value = PermissionCodes.API_AUTOMATION_IMPORT, scope = ApiAutomationPermissionScopes.SPEC)
    public ApiAutomationSpecDetailResponse parseSpec(@PathVariable UUID id) {
        return service.parseSpec(id);
    }

    @GetMapping("/specs/{id}/diff")
    @RequirePermission(value = PermissionCodes.API_AUTOMATION_READ, scope = ApiAutomationPermissionScopes.SPEC)
    public ApiAutomationDiffResponse diffSpec(@PathVariable UUID id) {
        return service.diffSpec(id);
    }

    @PostMapping("/specs/{id}/sync")
    @RequirePermission(value = PermissionCodes.API_AUTOMATION_IMPORT, scope = ApiAutomationPermissionScopes.SPEC)
    public ApiAutomationSyncResponse syncSpec(
            @PathVariable UUID id,
            @RequestBody(required = false) SyncApiAutomationSpecCommand command
    ) {
        return service.syncSpec(id, command);
    }

    @PostMapping("/generation-tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.API_AUTOMATION_GENERATE, scope = ApiAutomationPermissionScopes.GENERATION_REQUEST)
    public ApiAutomationGenerationTaskDetailResponse createGenerationTask(
            @Valid @RequestBody CreateApiAutomationGenerationTaskCommand command
    ) {
        return service.createGenerationTask(command);
    }

    @GetMapping("/generation-tasks/{id}")
    @RequirePermission(value = PermissionCodes.API_AUTOMATION_READ, scope = ApiAutomationPermissionScopes.GENERATION_TASK)
    public ApiAutomationGenerationTaskDetailResponse generationTask(@PathVariable UUID id) {
        return service.generationTaskDetail(id);
    }

    @PostMapping("/generation-tasks/{id}/script-bundles")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.API_AUTOMATION_GENERATE, scope = ApiAutomationPermissionScopes.GENERATION_TASK)
    public ApiAutomationScriptBundleResponse generateScriptBundle(@PathVariable UUID id) {
        return service.generateScriptBundle(id);
    }

    @PostMapping("/script-bundles/{id}/submit-review")
    @RequirePermission(value = PermissionCodes.API_AUTOMATION_REVIEW, scope = ApiAutomationPermissionScopes.SCRIPT_BUNDLE)
    public ApiAutomationScriptBundleResponse submitScriptBundleReview(
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewApiAutomationScriptBundleCommand command
    ) {
        return service.submitScriptBundleReview(id, command);
    }

    @PostMapping("/script-bundles/{id}/approve")
    @RequirePermission(value = PermissionCodes.API_AUTOMATION_REVIEW, scope = ApiAutomationPermissionScopes.SCRIPT_BUNDLE)
    public ApiAutomationScriptBundleResponse approveScriptBundle(
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewApiAutomationScriptBundleCommand command
    ) {
        return service.approveScriptBundle(id, command);
    }

    @PostMapping("/script-bundles/{id}/reject")
    @RequirePermission(value = PermissionCodes.API_AUTOMATION_REVIEW, scope = ApiAutomationPermissionScopes.SCRIPT_BUNDLE)
    public ApiAutomationScriptBundleResponse rejectScriptBundle(
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewApiAutomationScriptBundleCommand command
    ) {
        return service.rejectScriptBundle(id, command);
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.API_AUTOMATION_EXECUTE, scope = ApiAutomationPermissionScopes.RUN_REQUEST)
    public ApiAutomationRunDetailResponse createRun(@Valid @RequestBody CreateApiAutomationRunCommand command) {
        return service.createRun(command);
    }

    @GetMapping("/runs/{id}")
    @RequirePermission(value = PermissionCodes.API_AUTOMATION_READ, scope = ApiAutomationPermissionScopes.RUN)
    public ApiAutomationRunDetailResponse run(@PathVariable UUID id) {
        return service.runDetail(id);
    }
}
