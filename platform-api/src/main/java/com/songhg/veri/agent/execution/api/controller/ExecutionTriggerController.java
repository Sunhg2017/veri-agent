package com.songhg.veri.agent.execution.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.ApiResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.execution.application.ExecutionTriggerService;
import com.songhg.veri.agent.execution.application.command.CreateExecutionTriggerCommand;
import com.songhg.veri.agent.execution.application.command.UpdateExecutionTriggerCommand;
import com.songhg.veri.agent.execution.application.query.ExecutionTriggerEventPageRequest;
import com.songhg.veri.agent.execution.application.query.ExecutionTriggerPageRequest;
import com.songhg.veri.agent.execution.application.view.ExecutionTriggerDryRunResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionTriggerEventResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionTriggerResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionWebhookTriggerResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/execution")
public class ExecutionTriggerController {

    private final ExecutionTriggerService service;

    public ExecutionTriggerController(ExecutionTriggerService service) {
        this.service = service;
    }

    @PostMapping("/plans/{id}/triggers")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(value = PermissionCodes.EXECUTION_MANAGE, scope = ExecutionPermissionScopes.PLAN)
    public ExecutionTriggerResponse createTrigger(
            @PathVariable UUID id,
            @Valid @RequestBody CreateExecutionTriggerCommand command
    ) {
        return service.createTrigger(id, command);
    }

    @GetMapping("/plans/{id}/triggers")
    @RequirePermission(value = PermissionCodes.EXECUTION_READ, scope = ExecutionPermissionScopes.PLAN)
    public PageResponse<ExecutionTriggerResponse> triggers(
            @PathVariable UUID id,
            @Valid ExecutionTriggerPageRequest request
    ) {
        return service.triggers(id, request);
    }

    @GetMapping("/triggers/{id}")
    @RequirePermission(value = PermissionCodes.EXECUTION_READ, scope = ExecutionPermissionScopes.TRIGGER)
    public ExecutionTriggerResponse trigger(@PathVariable UUID id) {
        return service.trigger(id);
    }

    @PatchMapping("/triggers/{id}")
    @RequirePermission(value = PermissionCodes.EXECUTION_MANAGE, scope = ExecutionPermissionScopes.TRIGGER)
    public ExecutionTriggerResponse updateTrigger(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateExecutionTriggerCommand command
    ) {
        return service.updateTrigger(id, command);
    }

    @PostMapping("/triggers/{id}/dry-run")
    @RequirePermission(value = PermissionCodes.EXECUTION_READ, scope = ExecutionPermissionScopes.TRIGGER)
    public ExecutionTriggerDryRunResponse dryRun(@PathVariable UUID id) {
        return service.dryRun(id);
    }

    @GetMapping("/triggers/{id}/events")
    @RequirePermission(value = PermissionCodes.EXECUTION_READ, scope = ExecutionPermissionScopes.TRIGGER)
    public PageResponse<ExecutionTriggerEventResponse> events(
            @PathVariable UUID id,
            @Valid ExecutionTriggerEventPageRequest request
    ) {
        return service.events(id, request);
    }

    @PostMapping(path = "/webhooks/{id}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<ApiResponse<ExecutionWebhookTriggerResponse>> receiveWebhook(
            @PathVariable UUID id,
            @RequestHeader(name = "X-VA-Timestamp", required = false) String timestamp,
            @RequestHeader(name = "X-VA-Signature", required = false) String signature,
            @RequestHeader(name = "X-VA-Event-Id", required = false) String sourceEventId,
            @RequestBody(required = false) String rawPayload
    ) {
        ExecutionWebhookTriggerResponse response = service.receiveWebhook(
                id,
                rawPayload,
                timestamp,
                signature,
                sourceEventId
        );
        HttpStatus status = response.idempotentReplay() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity
                .status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.ok(response, TraceContext.getTraceId()));
    }
}
