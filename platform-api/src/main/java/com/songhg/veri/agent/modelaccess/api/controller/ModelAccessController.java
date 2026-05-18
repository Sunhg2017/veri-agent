package com.songhg.veri.agent.modelaccess.api.controller;

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
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
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
    public List<PromptTemplate> prompts(@RequestParam(required = false) String promptKey) {
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
            @Valid InvocationPageRequest pageRequest
    ) {
        return service.invocations(toQuery(pageRequest));
    }

    @GetMapping("/invocations/summary")
    public InvocationSummaryResponse invocationSummary(
            InvocationPageRequest pageRequest
    ) {
        return service.invocationSummary(toQuery(pageRequest));
    }

    @GetMapping(value = "/invocations/export", produces = "text/csv")
    public ResponseEntity<String> exportInvocations(
            InvocationPageRequest pageRequest
    ) {
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
            @RequestParam(required = false) String projectId
    ) {
        return service.costAlerts(projectId);
    }

    @GetMapping("/cost/report")
    public CostReportResponse costReport(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String projectId
    ) {
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
}
