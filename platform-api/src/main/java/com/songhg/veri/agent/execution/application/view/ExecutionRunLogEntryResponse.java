package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ExecutionRunLogEntryResponse(
        @Schema(description = "日志 ID")
        UUID id,
        @Schema(description = "运行 ID")
        UUID runId,
        @Schema(description = "节点运行 ID")
        UUID nodeRunId,
        @Schema(description = "节点 key")
        String nodeKey,
        @Schema(description = "日志级别")
        String level,
        @Schema(description = "日志阶段")
        String stage,
        @Schema(description = "日志消息")
        String message,
        @Schema(description = "脱敏元数据")
        Map<String, Object> metadata,
        @Schema(description = "事件时间")
        Instant eventAt,
        @Schema(description = "写入时间")
        Instant createdAt
) {
}
