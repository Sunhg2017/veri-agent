package com.songhg.veri.agent.modelaccess.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.modelaccess.application.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.ModelAccessService;
import com.songhg.veri.agent.modelaccess.api.request.CreatePromptRequest;
import com.songhg.veri.agent.modelaccess.api.request.CreateProviderRequest;
import com.songhg.veri.agent.modelaccess.api.request.InvocationPageRequest;
import com.songhg.veri.agent.modelaccess.api.request.InvokeModelRequest;
import com.songhg.veri.agent.modelaccess.api.request.ReviewPromptRequest;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/model-access")
public class ModelAccessController {

    private static final String READ_PERMISSION = "modelAccess:read";
    private static final String MANAGE_PERMISSION = "modelAccess:manage";
    private static final String EXPORT_PERMISSION = "modelAccess:export";
    private static final int STREAM_CHUNK_CODE_POINTS = 48;

    private final ModelAccessService service;
    private final AuthorizationService authorizationService;
    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;

    public ModelAccessController(
            ModelAccessService service,
            AuthorizationService authorizationService,
            AuditLogWriter auditLogWriter,
            ObjectMapper objectMapper
    ) {
        this.service = service;
        this.authorizationService = authorizationService;
        this.auditLogWriter = auditLogWriter;
        this.objectMapper = objectMapper;
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

    @PostMapping("/prompts/{id}/approve")
    public PromptTemplate approvePrompt(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReviewPromptRequest request
    ) {
        AuthUserPrincipal actor = requirePermission(MANAGE_PERMISSION);
        PromptTemplate prompt = service.approvePrompt(id, approvalActor(actor), request == null ? null : request.reviewNote());
        auditPromptReview(actor, prompt, "MODEL_PROMPT_APPROVE");
        return prompt;
    }

    @PostMapping("/prompts/{id}/reject")
    public PromptTemplate rejectPrompt(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReviewPromptRequest request
    ) {
        AuthUserPrincipal actor = requirePermission(MANAGE_PERMISSION);
        PromptTemplate prompt = service.rejectPrompt(id, approvalActor(actor), request == null ? null : request.reviewNote());
        auditPromptReview(actor, prompt, "MODEL_PROMPT_REJECT");
        return prompt;
    }

    @PostMapping("/invocations")
    public InvokeModelResponse invoke(
            @Valid @RequestBody InvokeModelRequest request
    ) {
        return service.invoke(request, invocationPrincipal());
    }

    @PostMapping(value = "/invocations/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "发起 SSE 流式模型调用",
            description = "复用同步 invocation 的请求体、策略、预算和调用日志链路；成功时返回 text/event-stream，依次输出 metadata、delta、done 事件。"
                    + " metadata 包含 invocationId、providerId、providerName、modelName、fallbackUsed、inputTokens、outputTokens、totalCost、traceId；"
                    + " delta 包含 index、content；done 包含 invocationId、finishReason。错误响应仍使用标准 JSON error envelope。"
    )
    @ApiResponse(responseCode = "200", description = "SSE event stream: metadata, delta, done")
    public ResponseEntity<StreamingResponseBody> invokeStream(
            @Valid @RequestBody InvokeModelRequest request
    ) {
        InvokeModelResponse response = service.invoke(request, invocationPrincipal());
        String traceId = TraceContext.getTraceId();
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_EVENT_STREAM, StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(outputStream -> {
                    writeSse(outputStream, "metadata", streamMetadata(response, traceId));
                    int index = 0;
                    for (String chunk : streamChunks(response.content())) {
                        writeSse(outputStream, "delta", Map.of(
                                "index", index++,
                                "content", chunk
                        ));
                    }
                    writeSse(outputStream, "done", Map.of(
                            "invocationId", response.invocationId(),
                            "finishReason", "stop"
                    ));
                });
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

    private void auditPromptReview(AuthUserPrincipal actor, PromptTemplate prompt, String action) {
        auditLogWriter.record(AuditLogWriter.success(
                actor,
                action,
                "ma_prompt_template",
                prompt.id().toString(),
                prompt.promptKey() + ":v" + prompt.version() + ":" + prompt.approvalStatus()
        ));
    }

    private String approvalActor(AuthUserPrincipal actor) {
        return actor == null ? "system" : actor.username();
    }

    private Map<String, Object> streamMetadata(InvokeModelResponse response, String traceId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("invocationId", response.invocationId());
        metadata.put("providerId", response.providerId());
        metadata.put("providerName", response.providerName());
        metadata.put("modelName", response.modelName());
        metadata.put("fallbackUsed", response.fallbackUsed());
        metadata.put("inputTokens", response.inputTokens());
        metadata.put("outputTokens", response.outputTokens());
        metadata.put("totalCost", response.totalCost());
        metadata.put("traceId", traceId);
        return metadata;
    }

    private List<String> streamChunks(String content) {
        String safeContent = content == null ? "" : content;
        if (safeContent.isEmpty()) {
            return List.of();
        }
        int[] codePoints = safeContent.codePoints().toArray();
        java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
        for (int index = 0; index < codePoints.length; index += STREAM_CHUNK_CODE_POINTS) {
            int length = Math.min(STREAM_CHUNK_CODE_POINTS, codePoints.length - index);
            chunks.add(new String(codePoints, index, length));
        }
        return chunks;
    }

    private void writeSse(java.io.OutputStream outputStream, String event, Object data) throws java.io.IOException {
        String payload = "event: " + event + "\n"
                + "data: " + objectMapper.writeValueAsString(data) + "\n\n";
        outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }
}
