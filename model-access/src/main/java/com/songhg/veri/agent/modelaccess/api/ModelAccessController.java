package com.songhg.veri.agent.modelaccess.api;

import com.songhg.veri.agent.modelaccess.application.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.ModelAccessService;
import com.songhg.veri.agent.modelaccess.common.PageResponse;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import com.songhg.veri.agent.modelaccess.domain.ProviderStatus;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    private final ModelAccessService service;

    public ModelAccessController(ModelAccessService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public ProviderHealthResponse health() {
        return new ProviderHealthResponse(
                "model-access",
                "UP",
                service.enabledProviderCount(),
                service.activePromptCount()
        );
    }

    @GetMapping("/providers")
    public List<ModelProviderConfig> providers() {
        return service.providers();
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    public ModelProviderConfig createProvider(@Valid @RequestBody CreateProviderRequest request) {
        return service.createProvider(request);
    }

    @PutMapping("/providers/{id}")
    public ModelProviderConfig updateProvider(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProviderRequest request
    ) {
        return service.updateProvider(id, request);
    }

    @PostMapping("/providers/{id}/enable")
    public ModelProviderConfig enableProvider(@PathVariable UUID id) {
        return service.setProviderStatus(id, ProviderStatus.ENABLED);
    }

    @PostMapping("/providers/{id}/disable")
    public ModelProviderConfig disableProvider(@PathVariable UUID id) {
        return service.setProviderStatus(id, ProviderStatus.DISABLED);
    }

    @PostMapping("/providers/{id}/check")
    public ProviderCheckResponse checkProvider(@PathVariable UUID id) {
        return service.checkProvider(id);
    }

    @GetMapping("/prompts")
    public List<PromptTemplate> prompts(@RequestParam(name = "prompt_key", required = false) String promptKey) {
        return service.prompts(promptKey);
    }

    @PostMapping("/prompts")
    @ResponseStatus(HttpStatus.CREATED)
    public PromptTemplate createPrompt(@Valid @RequestBody CreatePromptRequest request) {
        return service.createPrompt(request);
    }

    @PostMapping("/prompts/{id}/activate")
    public PromptTemplate activatePrompt(@PathVariable UUID id) {
        return service.activatePrompt(id);
    }

    @PostMapping("/invocations")
    public InvokeModelResponse invoke(
            @Valid @RequestBody InvokeModelRequest request,
            @AuthenticationPrincipal ServicePrincipal principal
    ) {
        return service.invoke(request, principal);
    }

    @GetMapping("/invocations")
    public PageResponse<InvocationRecord> invocations(
            @RequestParam(name = "project_id", required = false) String projectId,
            @RequestParam(name = "application_id", required = false) String applicationId,
            @RequestParam(name = "sensitivity_level", required = false) String sensitivityLevel,
            @RequestParam(required = false) InvocationStatus status,
            @RequestParam(name = "provider_id", required = false) UUID providerId,
            @RequestParam(name = "actor_service", required = false) String actorService,
            @RequestParam(name = "start_time", required = false) Instant startTime,
            @RequestParam(name = "end_time", required = false) Instant endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return service.invocations(new InvocationQuery(
                projectId,
                applicationId,
                sensitivityLevel,
                status,
                providerId,
                actorService,
                startTime,
                endTime,
                page,
                size
        ));
    }

    @GetMapping("/invocations/summary")
    public InvocationSummaryResponse invocationSummary(
            @RequestParam(name = "project_id", required = false) String projectId,
            @RequestParam(name = "application_id", required = false) String applicationId,
            @RequestParam(name = "sensitivity_level", required = false) String sensitivityLevel,
            @RequestParam(required = false) InvocationStatus status,
            @RequestParam(name = "provider_id", required = false) UUID providerId,
            @RequestParam(name = "actor_service", required = false) String actorService,
            @RequestParam(name = "start_time", required = false) Instant startTime,
            @RequestParam(name = "end_time", required = false) Instant endTime
    ) {
        return service.invocationSummary(new InvocationQuery(
                projectId,
                applicationId,
                sensitivityLevel,
                status,
                providerId,
                actorService,
                startTime,
                endTime,
                0,
                200
        ));
    }

    @GetMapping(value = "/invocations/export", produces = "text/csv")
    public ResponseEntity<String> exportInvocations(
            @RequestParam(name = "project_id", required = false) String projectId,
            @RequestParam(name = "application_id", required = false) String applicationId,
            @RequestParam(name = "sensitivity_level", required = false) String sensitivityLevel,
            @RequestParam(required = false) InvocationStatus status,
            @RequestParam(name = "provider_id", required = false) UUID providerId,
            @RequestParam(name = "actor_service", required = false) String actorService,
            @RequestParam(name = "start_time", required = false) Instant startTime,
            @RequestParam(name = "end_time", required = false) Instant endTime
    ) {
        String csv = service.exportInvocationsCsv(new InvocationQuery(
                projectId,
                applicationId,
                sensitivityLevel,
                status,
                providerId,
                actorService,
                startTime,
                endTime,
                0,
                200
        ));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wp2-invocations.csv\"")
                .body(csv);
    }

    @GetMapping("/cost/alerts")
    public List<CostAlertResponse> costAlerts(
            @RequestParam(name = "project_id", required = false) String projectId
    ) {
        return service.costAlerts(projectId);
    }

    @GetMapping("/cost/report")
    public CostReportResponse costReport(
            @RequestParam(name = "start_date", required = false) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) LocalDate endDate,
            @RequestParam(name = "project_id", required = false) String projectId
    ) {
        return service.costReport(startDate, endDate, projectId);
    }
}
