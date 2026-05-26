package com.songhg.veri.agent.modelaccess.api.response;

import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ModelInvocationJobResponse(
        @Schema(description = "异步任务 ID")
        UUID jobId,
        @Schema(description = "业务状态")
        ModelInvocationJobStatus status,
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
        @Schema(description = "链路追踪 ID")
        String traceId,
        @Schema(description = "模型或下游服务响应内容")
        InvokeModelResponse response
) {
}
