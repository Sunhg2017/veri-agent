package com.songhg.veri.agent.modelaccess.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.modelaccess.application.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.ModelAccessService;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationJobService;
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
import com.songhg.veri.agent.modelaccess.api.response.ModelInvocationJobResponse;
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
import com.songhg.veri.agent.common.error.PlatformAccessDeniedException;
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

@ApiVersion
@RestController
@RequestMapping("/api/v1/model-access")
public class ModelAccessController {

    private static final String READ_PERMISSION = "modelAccess:read";
    private static final String MANAGE_PERMISSION = "modelAccess:manage";
    private static final String EXPORT_PERMISSION = "modelAccess:export";
    private static final int STREAM_CHUNK_CODE_POINTS = 48;

    private final ModelAccessService service;
    private final ModelInvocationJobService jobService;
    private final AuthorizationService authorizationService;
    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;

    public ModelAccessController(
            ModelAccessService service,
            ModelInvocationJobService jobService,
            AuthorizationService authorizationService,
            AuditLogWriter auditLogWriter,
            ObjectMapper objectMapper
    ) {
        this.service = service;
        this.jobService = jobService;
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
    @RequirePermission(READ_PERMISSION)
    public List<ModelProviderConfig> providers() {
        return service.providers();
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(MANAGE_PERMISSION)
    public ModelProviderConfig createProvider(@Valid @RequestBody CreateProviderRequest request) {
        return service.createProvider(request);
    }

    @PutMapping("/providers/{id}")
    @RequirePermission(MANAGE_PERMISSION)
    public ModelProviderConfig updateProvider(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProviderRequest request
    ) {
        return service.updateProvider(id, request);
    }

    @PostMapping("/providers/{id}/enable")
    @RequirePermission(MANAGE_PERMISSION)
    public ModelProviderConfig enableProvider(@PathVariable UUID id) {
        return service.setProviderStatus(id, ProviderStatus.ENABLED);
    }

    @PostMapping("/providers/{id}/disable")
    @RequirePermission(MANAGE_PERMISSION)
    public ModelProviderConfig disableProvider(@PathVariable UUID id) {
        return service.setProviderStatus(id, ProviderStatus.DISABLED);
    }

    @PostMapping("/providers/{id}/check")
    @RequirePermission(MANAGE_PERMISSION)
    public ProviderCheckResponse checkProvider(@PathVariable UUID id) {
        return service.checkProvider(id);
    }

    @GetMapping("/providers/{id}/resilience")
    @RequirePermission(READ_PERMISSION)
    public ProviderResilienceResponse providerResilience(@PathVariable UUID id) {
        return service.providerResilience(id);
    }

    @PostMapping("/providers/{id}/circuit/reset")
    @RequirePermission(MANAGE_PERMISSION)
    public ProviderResilienceResponse resetProviderCircuit(@PathVariable UUID id) {
        return service.resetProviderCircuit(id);
    }

    @GetMapping("/prompts")
    @RequirePermission(READ_PERMISSION)
    public List<PromptTemplate> prompts(@RequestParam(required = false) String promptKey) {
        return service.prompts(promptKey);
    }

    @PostMapping("/prompts")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(MANAGE_PERMISSION)
    public PromptTemplate createPrompt(@Valid @RequestBody CreatePromptRequest request) {
        AuthUserPrincipal actor = authorizationService.currentUserPrincipal();
        PromptTemplate prompt = service.createPrompt(request);
        if (prompt.status() == PromptStatus.ACTIVE) {
            auditPromptActivation(actor, prompt, "MODEL_PROMPT_CREATE_ACTIVATE");
        }
        return prompt;
    }

    @PostMapping("/prompts/{id}/activate")
    @RequirePermission(MANAGE_PERMISSION)
    public PromptTemplate activatePrompt(@PathVariable UUID id) {
        AuthUserPrincipal actor = authorizationService.currentUserPrincipal();
        PromptTemplate prompt = service.activatePrompt(id);
        auditPromptActivation(actor, prompt, "MODEL_PROMPT_ACTIVATE");
        return prompt;
    }

    @PostMapping("/prompts/{id}/approve")
    @RequirePermission(MANAGE_PERMISSION)
    public PromptTemplate approvePrompt(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReviewPromptRequest request
    ) {
        AuthUserPrincipal actor = authorizationService.currentUserPrincipal();
        PromptTemplate prompt = service.approvePrompt(id, approvalActor(actor), request == null ? null : request.reviewNote());
        auditPromptReview(actor, prompt, "MODEL_PROMPT_APPROVE");
        return prompt;
    }

    @PostMapping("/prompts/{id}/reject")
    @RequirePermission(MANAGE_PERMISSION)
    public PromptTemplate rejectPrompt(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReviewPromptRequest request
    ) {
        AuthUserPrincipal actor = authorizationService.currentUserPrincipal();
        PromptTemplate prompt = service.rejectPrompt(id, approvalActor(actor), request == null ? null : request.reviewNote());
        auditPromptReview(actor, prompt, "MODEL_PROMPT_REJECT");
        return prompt;
    }

    @PostMapping("/invocations")
    @RequirePermission(MANAGE_PERMISSION)
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
    @RequirePermission(MANAGE_PERMISSION)
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

    @PostMapping("/invocations/jobs")
    @Operation(
            summary = "提交异步模型调用任务",
            description = "复用同步 invocation 的请求体、策略、预算、供应商调用和调用日志链路；返回单进程内存 jobId，可查询或 best-effort 取消。"
    )
    @ApiResponse(responseCode = "202", description = "异步任务已提交，状态为 QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED")
    @RequirePermission(MANAGE_PERMISSION)
    public ResponseEntity<ModelInvocationJobResponse> submitInvocationJob(
            @Valid @RequestBody InvokeModelRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(jobService.submit(request, invocationPrincipal()));
    }

    @GetMapping("/invocations/jobs/{jobId}")
    @Operation(
            summary = "查询异步模型调用任务",
            description = "返回 job 状态、时间戳、关联 invocationId、错误摘要和成功响应。job registry 为单进程内存态，服务重启后不保留。"
    )
    @RequirePermission(MANAGE_PERMISSION)
    public ModelInvocationJobResponse invocationJob(@PathVariable UUID jobId) {
        invocationPrincipal();
        return jobService.get(jobId);
    }

    @PostMapping("/invocations/jobs/{jobId}/cancel")
    @Operation(
            summary = "取消异步模型调用任务",
            description = "对未开始任务可稳定取消；运行中任务会 best-effort interrupt，若已完成则返回当前终态。"
    )
    @RequirePermission(MANAGE_PERMISSION)
    public ModelInvocationJobResponse cancelInvocationJob(@PathVariable UUID jobId) {
        invocationPrincipal();
        return jobService.cancel(jobId);
    }

    @GetMapping("/invocations")
    @RequirePermission(READ_PERMISSION)
    public PageResponse<InvocationRecord> invocations(
            @Valid InvocationPageRequest pageRequest
    ) {
        return service.invocations(toQuery(pageRequest));
    }

    @GetMapping("/invocations/summary")
    @RequirePermission(READ_PERMISSION)
    public InvocationSummaryResponse invocationSummary(
            InvocationPageRequest pageRequest
    ) {
        return service.invocationSummary(toQuery(pageRequest));
    }

    @GetMapping(value = "/invocations/export", produces = "text/csv")
    @RequirePermission(EXPORT_PERMISSION)
    public ResponseEntity<StreamingResponseBody> exportInvocations(
            InvocationPageRequest pageRequest
    ) {
        pageRequest.setIndex(0);
        pageRequest.setSize(200);
        InvocationQuery exportQuery = toQuery(pageRequest);
        StreamingResponseBody body = outputStream -> service.writeInvocationsCsv(exportQuery, outputStream);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wp2-invocations.csv\"")
                .body(body);
    }

    @GetMapping("/cost/alerts")
    @RequirePermission(READ_PERMISSION)
    public List<CostAlertResponse> costAlerts(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String actorService
    ) {
        return service.costAlerts(projectId, actorService);
    }

    @GetMapping("/cost/report")
    @RequirePermission(READ_PERMISSION)
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

    private ServicePrincipal invocationPrincipal() {
        ServicePrincipal servicePrincipal = authorizationService.currentServicePrincipal();
        if (servicePrincipal != null) {
            return servicePrincipal;
        }
        AuthUserPrincipal principal = authorizationService.currentUserPrincipal();
        if (principal != null) {
            return new ServicePrincipal("model-access-console", principal.userId().toString());
        }
        throw new PlatformAccessDeniedException(MANAGE_PERMISSION);
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
