package com.songhg.veri.agent.document.application.view;

import com.songhg.veri.agent.document.domain.WebhookEventStatus;
import com.songhg.veri.agent.document.domain.WebhookSignatureStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record DocumentWebhookEventResponse(
        @Schema(description = "主键 ID。")
        UUID id,
        @Schema(description = "文档输入源 ID。")
        UUID sourceId,
        @Schema(description = "文档导入任务 ID。")
        UUID importId,
        @Schema(description = "文档输入源编码。")
        String sourceCode,
        @Schema(description = "外部事件 ID。")
        String eventId,
        @Schema(description = "幂等键，用于重复请求回放和并发去重。")
        String idempotencyKey,
        @Schema(description = "外部事件类型。")
        String eventType,
        @Schema(description = "事件协议版本。")
        String eventVersion,
        @Schema(description = "签名校验状态。")
        WebhookSignatureStatus signatureStatus,
        @Schema(description = "业务状态。")
        WebhookEventStatus status,
        @Schema(description = "请求载荷摘要。")
        String payloadDigest,
        @Schema(description = "错误摘要。")
        String errorMessage,
        @Schema(description = "已重试次数。")
        int retryCount,
        @Schema(description = "重放操作人。")
        String replayBy,
        @Schema(description = "重放时间。")
        Instant replayAt,
        @Schema(description = "重放链路追踪 ID。")
        String replayTraceId,
        @Schema(description = "接收时间。")
        Instant receivedAt,
        @Schema(description = "处理完成时间。")
        Instant processedAt
) {
}
