package com.songhg.veri.agent.apiautomation.api.controller;

import com.songhg.veri.agent.apiautomation.application.ApiAutomationService;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationSpecCommand;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecPageRequest;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationHealthResponse;
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
}
