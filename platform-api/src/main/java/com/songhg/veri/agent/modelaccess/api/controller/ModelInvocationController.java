package com.songhg.veri.agent.modelaccess.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.modelaccess.api.mapper.ModelAccessApiMapper;
import com.songhg.veri.agent.modelaccess.api.request.InvocationPageRequest;
import com.songhg.veri.agent.modelaccess.api.request.InvokeModelRequest;
import com.songhg.veri.agent.modelaccess.api.response.InvocationSummaryResponse;
import com.songhg.veri.agent.modelaccess.api.response.InvokeModelResponse;
import com.songhg.veri.agent.modelaccess.api.response.ModelInvocationJobResponse;
import com.songhg.veri.agent.modelaccess.application.ModelAccessActorResolver;
import com.songhg.veri.agent.modelaccess.application.ModelAccessService;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationJobService;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import com.songhg.veri.agent.modelaccess.application.query.InvocationQuery;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@ApiVersion
@RestController
@RequestMapping("/api/v1/model-access/invocations")
public class ModelInvocationController {

    private final ModelAccessService service;
    private final ModelInvocationService invocationService;
    private final ModelInvocationJobService jobService;
    private final ModelAccessActorResolver actorResolver;
    private final ModelInvocationStreamSupport streamSupport;
    private final ModelAccessApiMapper apiMapper;

    public ModelInvocationController(
            ModelAccessService service,
            ModelInvocationService invocationService,
            ModelInvocationJobService jobService,
            ModelAccessActorResolver actorResolver,
            ModelInvocationStreamSupport streamSupport,
            ModelAccessApiMapper apiMapper
    ) {
        this.service = service;
        this.invocationService = invocationService;
        this.jobService = jobService;
        this.actorResolver = actorResolver;
        this.streamSupport = streamSupport;
        this.apiMapper = apiMapper;
    }

    @PostMapping
    @RequirePermission(PermissionCodes.MODEL_ACCESS_MANAGE)
    public InvokeModelResponse invoke(@Valid @RequestBody InvokeModelRequest request) {
        return apiMapper.toResponse(invocationService.invoke(apiMapper.toCommand(request), invocationPrincipal()));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "发起 SSE 流式模型调用",
            description = "复用同步 invocation 的请求体、策略、预算和调用日志链路；成功时返回 text/event-stream，依次输出 metadata、delta、done 事件。"
                    + " metadata 包含 invocationId、providerId、providerName、modelName、fallbackUsed、inputTokens、outputTokens、totalCost、traceId；"
                    + " delta 包含 index、content；done 包含 invocationId、finishReason。错误响应仍使用标准 JSON error envelope。"
    )
    @ApiResponse(responseCode = "200", description = "SSE event stream: metadata, delta, done")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_MANAGE)
    public ResponseEntity<StreamingResponseBody> invokeStream(@Valid @RequestBody InvokeModelRequest request) {
        InvokeModelResponse response = apiMapper.toResponse(
                invocationService.invoke(apiMapper.toCommand(request), invocationPrincipal())
        );
        String traceId = TraceContext.getTraceId();
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_EVENT_STREAM, StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(streamSupport.stream(response, traceId));
    }

    @PostMapping("/jobs")
    @Operation(
            summary = "提交异步模型调用任务",
            description = "复用同步 invocation 的请求体、策略、预算、供应商调用和调用日志链路；返回持久化 jobId，可查询或 best-effort 取消。"
    )
    @ApiResponse(responseCode = "202", description = "异步任务已提交，状态为 QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_MANAGE)
    public ResponseEntity<ModelInvocationJobResponse> submitInvocationJob(
            @Valid @RequestBody InvokeModelRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(apiMapper.toResponse(jobService.submit(apiMapper.toCommand(request), invocationPrincipal())));
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(
            summary = "查询异步模型调用任务",
            description = "返回 job 状态、时间戳、关联 invocationId、错误摘要和成功响应。job 状态和结果持久化保存，服务重启后仍可查询。"
    )
    @RequirePermission(PermissionCodes.MODEL_ACCESS_MANAGE)
    public ModelInvocationJobResponse invocationJob(@PathVariable UUID jobId) {
        invocationPrincipal();
        return apiMapper.toResponse(jobService.get(jobId));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(
            summary = "取消异步模型调用任务",
            description = "对未开始任务可稳定取消；运行中任务会 best-effort interrupt，若已完成则返回当前终态。"
    )
    @RequirePermission(PermissionCodes.MODEL_ACCESS_MANAGE)
    public ModelInvocationJobResponse cancelInvocationJob(@PathVariable UUID jobId) {
        invocationPrincipal();
        return apiMapper.toResponse(jobService.cancel(jobId));
    }

    @GetMapping
    @RequirePermission(PermissionCodes.MODEL_ACCESS_READ)
    public PageResponse<InvocationRecord> invocations(@Valid InvocationPageRequest pageRequest) {
        return service.invocations(apiMapper.toQuery(pageRequest));
    }

    @GetMapping("/summary")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_READ)
    public InvocationSummaryResponse invocationSummary(InvocationPageRequest pageRequest) {
        return apiMapper.toResponse(service.invocationSummary(apiMapper.toQuery(pageRequest)));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @RequirePermission(PermissionCodes.MODEL_ACCESS_EXPORT)
    public ResponseEntity<StreamingResponseBody> exportInvocations(InvocationPageRequest pageRequest) {
        pageRequest.setIndex(0);
        pageRequest.setSize(200);
        InvocationQuery exportQuery = apiMapper.toQuery(pageRequest);
        StreamingResponseBody body = outputStream -> service.writeInvocationsCsv(exportQuery, outputStream);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wp2-invocations.csv\"")
                .body(body);
    }

    private ServicePrincipal invocationPrincipal() {
        return actorResolver.invocationPrincipal();
    }

}
