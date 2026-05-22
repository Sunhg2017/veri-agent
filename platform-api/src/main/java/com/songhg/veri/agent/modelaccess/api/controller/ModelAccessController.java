package com.songhg.veri.agent.modelaccess.api.controller;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.modelaccess.application.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.ModelAccessService;
import com.songhg.veri.agent.modelaccess.api.request.CreatePromptRequest;
import com.songhg.veri.agent.modelaccess.api.request.CreateProviderRequest;
import com.songhg.veri.agent.modelaccess.api.request.InvocationPageRequest;
import com.songhg.veri.agent.modelaccess.api.request.InvokeModelRequest;
import com.songhg.veri.agent.modelaccess.api.request.UpdateProviderRequest;
import com.songhg.veri.agent.modelaccess.api.response.CostAlertResponse;
import com.songhg.veri.agent.modelaccess.api.response.CostReportResponse;
import com.songhg.veri.agent.modelaccess.api.response.InvocationSummaryResponse;
import com.songhg.veri.agent.modelaccess.api.response.InvokeModelResponse;
import com.songhg.veri.agent.modelaccess.api.response.ProviderCheckResponse;
import com.songhg.veri.agent.modelaccess.api.response.ProviderHealthResponse;
import com.songhg.veri.agent.modelaccess.api.response.ProviderResilienceResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptStatus;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import com.songhg.veri.agent.modelaccess.domain.ProviderStatus;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/model-access")
public class ModelAccessController {

    private static final String READ_PERMISSION = "modelAccess:read";
    private static final String MANAGE_PERMISSION = "modelAccess:manage";
    private static final String EXPORT_PERMISSION = "modelAccess:export";

    private final ModelAccessService service;
    private final AuthorizationService authorizationService;
    private final AuditLogWriter auditLogWriter;

    public ModelAccessController(
            ModelAccessService service,
            AuthorizationService authorizationService,
            AuditLogWriter auditLogWriter
    ) {
        this.service = service;
        this.authorizationService = authorizationService;
        this.auditLogWriter = auditLogWriter;
    }

    @GetMapping("/health")
    public ProviderHealthResponse health() {
        return new ProviderHealthResponse(
                "model-access",
                "UP",
                service.enabledProviderCount(),
                service.activePromptCount(),
                service.providerRateLimitEnabled(),
                service.providerRateLimitMaxRequests(),
                service.providerRateLimitWindowSeconds(),
                service.providerConcurrencyLimitEnabled(),
                service.providerMaxConcurrentRequests(),
                service.openCircuitProviderCount()
        );
    }

    @GetMapping("/providers")
    public List<ModelProviderConfig> providers() {
        requirePermission(READ_PERMISSION);
        return service.providers();
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    public ModelProviderConfig createProvider(@Valid @RequestBody CreateProviderRequest request) {
        requirePermission(MANAGE_PERMISSION);
        return service.createProvider(request);
    }

    @PutMapping("/providers/{id}")
    public ModelProviderConfig updateProvider(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProviderRequest request
    ) {
        requirePermission(MANAGE_PERMISSION);
        return service.updateProvider(id, request);
    }

    @PostMapping("/providers/{id}/enable")
    public ModelProviderConfig enableProvider(@PathVariable UUID id) {
        requirePermission(MANAGE_PERMISSION);
        return service.setProviderStatus(id, ProviderStatus.ENABLED);
    }

    @PostMapping("/providers/{id}/disable")
    public ModelProviderConfig disableProvider(@PathVariable UUID id) {
        requirePermission(MANAGE_PERMISSION);
        return service.setProviderStatus(id, ProviderStatus.DISABLED);
    }

    @PostMapping("/providers/{id}/check")
    public ProviderCheckResponse checkProvider(@PathVariable UUID id) {
        requirePermission(MANAGE_PERMISSION);
        return service.checkProvider(id);
    }

    @GetMapping("/providers/{id}/resilience")
    public ProviderResilienceResponse providerResilience(@PathVariable UUID id) {
        requirePermission(READ_PERMISSION);
        return service.providerResilience(id);
    }

    @PostMapping("/providers/{id}/circuit/reset")
    public ProviderResilienceResponse resetProviderCircuit(@PathVariable UUID id) {
        requirePermission(MANAGE_PERMISSION);
        return service.resetProviderCircuit(id);
    }

    @GetMapping("/prompts")
    public List<PromptTemplate> prompts(@RequestParam(required = false) String promptKey) {
        requirePermission(READ_PERMISSION);
        return service.prompts(promptKey);
    }

    @PostMapping("/prompts")
    @ResponseStatus(HttpStatus.CREATED)
    public PromptTemplate createPrompt(@Valid @RequestBody CreatePromptRequest request) {
        AuthUserPrincipal actor = requirePermission(MANAGE_PERMISSION);
        PromptTemplate prompt = service.createPrompt(request);
        if (prompt.status() == PromptStatus.ACTIVE) {
            auditPromptActivation(actor, prompt, "MODEL_PROMPT_CREATE_ACTIVATE");
        }
        return prompt;
    }

    @PostMapping("/prompts/{id}/activate")
    public PromptTemplate activatePrompt(@PathVariable UUID id) {
        AuthUserPrincipal actor = requirePermission(MANAGE_PERMISSION);
        PromptTemplate prompt = service.activatePrompt(id);
        auditPromptActivation(actor, prompt, "MODEL_PROMPT_ACTIVATE");
        return prompt;
    }

    @PostMapping("/invocations")
    public InvokeModelResponse invoke(
            @Valid @RequestBody InvokeModelRequest request
    ) {
        return service.invoke(request, invocationPrincipal());
    }

    @GetMapping("/invocations")
    public PageResponse<InvocationRecord> invocations(
            @Valid InvocationPageRequest pageRequest
    ) {
        requirePermission(READ_PERMISSION);
        return service.invocations(toQuery(pageRequest));
    }

    @GetMapping("/invocations/summary")
    public InvocationSummaryResponse invocationSummary(
            InvocationPageRequest pageRequest
    ) {
        requirePermission(READ_PERMISSION);
        return service.invocationSummary(toQuery(pageRequest));
    }

    @GetMapping(value = "/invocations/export", produces = "text/csv")
    public ResponseEntity<String> exportInvocations(
            InvocationPageRequest pageRequest
    ) {
        requirePermission(EXPORT_PERMISSION);
        pageRequest.setIndex(0);
        pageRequest.setSize(200);
        String csv = service.exportInvocationsCsv(toQuery(pageRequest));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wp2-invocations.csv\"")
                .body(csv);
    }

    @GetMapping("/cost/alerts")
    public List<CostAlertResponse> costAlerts(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String actorService
    ) {
        requirePermission(READ_PERMISSION);
        return service.costAlerts(projectId, actorService);
    }

    @GetMapping("/cost/report")
    public CostReportResponse costReport(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String projectId
    ) {
        requirePermission(READ_PERMISSION);
        return service.costReport(startDate, endDate, projectId);
    }

    private InvocationQuery toQuery(InvocationPageRequest pageRequest) {
        return new InvocationQuery(
                pageRequest.getProjectId(),
                pageRequest.getApplicationId(),
                pageRequest.getSensitivityLevel(),
                pageRequest.getStatus(),
                pageRequest.getProviderId(),
                pageRequest.getActorService(),
                pageRequest.getStartTime(),
                pageRequest.getEndTime(),
                pageRequest.toPageQuery()
        );
    }

    private AuthUserPrincipal requirePermission(String permission) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ServicePrincipal) {
            return null;
        }
        if (authentication != null && authentication.getPrincipal() instanceof AuthUserPrincipal principal) {
            authorizationService.require(principal, permission);
            return principal;
        }
        throw new AccessDeniedException("缺少权限：" + permission);
    }

    private ServicePrincipal invocationPrincipal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ServicePrincipal principal) {
            return principal;
        }
        if (authentication != null && authentication.getPrincipal() instanceof AuthUserPrincipal principal) {
            authorizationService.require(principal, MANAGE_PERMISSION);
            return new ServicePrincipal("model-access-console", principal.userId().toString());
        }
        throw new AccessDeniedException("缺少权限：" + MANAGE_PERMISSION);
    }

    private void auditPromptActivation(AuthUserPrincipal actor, PromptTemplate prompt, String action) {
        auditLogWriter.record(AuditLogWriter.success(
                actor,
                action,
                "ma_prompt_template",
                prompt.id().toString(),
                prompt.promptKey() + ":v" + prompt.version()
        ));
    }
}
