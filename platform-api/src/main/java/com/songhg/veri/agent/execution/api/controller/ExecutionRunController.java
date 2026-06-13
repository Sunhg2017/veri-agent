package com.songhg.veri.agent.execution.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.ApiResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.execution.application.ExecutionRunService;
import com.songhg.veri.agent.execution.application.command.TriggerExecutionRunCommand;
import com.songhg.veri.agent.execution.application.query.ExecutionRunPageRequest;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunSummaryResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/execution")
public class ExecutionRunController {

    private final ExecutionRunService service;

    public ExecutionRunController(ExecutionRunService service) {
        this.service = service;
    }

    @PostMapping("/plans/{id}/runs")
    @RequirePermission(value = PermissionCodes.EXECUTION_TRIGGER, scope = ExecutionPermissionScopes.PLAN)
    public ResponseEntity<ApiResponse<ExecutionRunDetailResponse>> triggerManualRun(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) TriggerExecutionRunCommand command
    ) {
        ExecutionRunDetailResponse response = service.triggerManualRun(id, command);
        HttpStatus status = response.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity
                .status(status)
                .body(ApiResponse.ok(response, TraceContext.getTraceId()));
    }

    @GetMapping("/runs")
    @RequirePermission(value = PermissionCodes.EXECUTION_READ, scope = ExecutionPermissionScopes.RUN_LIST)
    public PageResponse<ExecutionRunSummaryResponse> runs(@Valid ExecutionRunPageRequest request) {
        return service.runs(request);
    }

    @GetMapping("/runs/{id}")
    @RequirePermission(value = PermissionCodes.EXECUTION_READ, scope = ExecutionPermissionScopes.RUN)
    public ExecutionRunDetailResponse run(@PathVariable UUID id) {
        return service.run(id);
    }

    @PostMapping("/runs/{id}/cancel")
    @RequirePermission(value = PermissionCodes.EXECUTION_TRIGGER, scope = ExecutionPermissionScopes.RUN)
    public ExecutionRunDetailResponse cancelRun(@PathVariable UUID id) {
        return service.cancelRun(id);
    }

    @PostMapping("/runs/{id}/retry")
    @RequirePermission(value = PermissionCodes.EXECUTION_TRIGGER, scope = ExecutionPermissionScopes.RUN)
    public ExecutionRunDetailResponse retryRun(@PathVariable UUID id) {
        return service.retryRun(id);
    }
}
