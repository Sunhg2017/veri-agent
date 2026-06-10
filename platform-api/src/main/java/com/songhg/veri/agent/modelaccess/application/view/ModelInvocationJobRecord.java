package com.songhg.veri.agent.modelaccess.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ModelInvocationJobRecord(
        @Schema(description = "异步任务 ID")
        UUID jobId,
        @Schema(description = "业务状态")
        ModelInvocationJobStatus status,
        @Schema(description = "请求 JSON 快照，禁止包含密钥明文")
        String requestJson,
        @Schema(description = "发起内部调用的服务标识")
        String actorService,
        @Schema(description = "代理执行用户 ID")
        String delegatedUserId,
        @Schema(description = "提交任务时的用户角色编码快照，逗号分隔")
        String principalRoles,
        @Schema(description = "链路追踪 ID")
        String traceId,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "开始时间")
        Instant startedAt,
        @Schema(description = "结束时间")
        Instant finishedAt,
        @Schema(description = "模型调用记录 ID")
        UUID invocationId,
        @Schema(description = "错误编码")
        String errorCode,
        @Schema(description = "错误摘要")
        String errorMessage,
        @Schema(description = "响应 JSON 快照")
        String responseJson
) {

    public ModelInvocationJobRecord(
            UUID jobId,
            ModelInvocationJobStatus status,
            String requestJson,
            String actorService,
            String delegatedUserId,
            String traceId,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            UUID invocationId,
            String errorCode,
            String errorMessage,
            String responseJson
    ) {
        this(
                jobId,
                status,
                requestJson,
                actorService,
                delegatedUserId,
                null,
                traceId,
                createdAt,
                startedAt,
                finishedAt,
                invocationId,
                errorCode,
                errorMessage,
                responseJson
        );
    }
}
