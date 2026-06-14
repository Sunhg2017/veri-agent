package com.songhg.veri.agent.execution.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.execution.application.ExecutionPlanService;
import com.songhg.veri.agent.execution.application.command.CreateExecutionPlanCommand;
import com.songhg.veri.agent.execution.application.command.UpdateExecutionPlanCommand;
import com.songhg.veri.agent.execution.application.query.ExecutionPlanPageRequest;
import com.songhg.veri.agent.execution.application.view.ExecutionDryRunResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionPlanDetailResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionPlanSummaryResponse;
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
@RequestMapping("/api/v1/execution/plans")
public class ExecutionPlanController {

    private final ExecutionPlanService service;

    public ExecutionPlanController(ExecutionPlanService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.EXECUTION_MANAGE, scope = ExecutionPermissionScopes.PLAN_REQUEST)
    public ExecutionPlanDetailResponse createPlan(@Valid @RequestBody CreateExecutionPlanCommand command) {
        return service.createPlan(command);
    }

    @GetMapping
    @RequirePermission(value = PermissionCodes.EXECUTION_READ, scope = ExecutionPermissionScopes.PLAN_LIST)
    public PageResponse<ExecutionPlanSummaryResponse> plans(@Valid ExecutionPlanPageRequest request) {
        return service.plans(request);
    }

    @GetMapping("/{id}")
    @RequirePermission(value = PermissionCodes.EXECUTION_READ, scope = ExecutionPermissionScopes.PLAN)
    public ExecutionPlanDetailResponse plan(@PathVariable UUID id) {
        return service.plan(id);
    }

    @PatchMapping("/{id}")
    @RequirePermission(value = PermissionCodes.EXECUTION_MANAGE, scope = ExecutionPermissionScopes.PLAN)
    public ExecutionPlanDetailResponse updatePlan(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateExecutionPlanCommand command
    ) {
        return service.updatePlan(id, command);
    }

    @PostMapping("/{id}/dry-run")
    @RequirePermission(value = PermissionCodes.EXECUTION_READ, scope = ExecutionPermissionScopes.PLAN)
    public ExecutionDryRunResponse dryRun(@PathVariable UUID id) {
        return service.dryRun(id);
    }

    @PostMapping("/{id}/archive")
    @RequirePermission(value = PermissionCodes.EXECUTION_MANAGE, scope = ExecutionPermissionScopes.PLAN)
    public ExecutionPlanDetailResponse archivePlan(@PathVariable UUID id) {
        return service.archivePlan(id);
    }
}
