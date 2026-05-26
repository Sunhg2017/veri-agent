package com.songhg.veri.agent.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentWebhookEvent(
        /** 主键 ID。 */
        UUID id,
        /** 来源 ID。 */
        UUID sourceId,
        /** 导入任务 ID。 */
        UUID importId,
        /** 来源编码。 */
        String sourceCode,
        /** 事件 ID。 */
        String eventId,
        /** 幂等键。 */
        String idempotencyKey,
        /** 事件类型。 */
        String eventType,
        /** 事件版本。 */
        String eventVersion,
        /** 签名状态。 */
        WebhookSignatureStatus signatureStatus,
        /** 业务状态。 */
        WebhookEventStatus status,
        /** 载荷摘要。 */
        String payloadDigest,
        /** 原始 webhook 请求载荷。 */
        String rawPayload,
        /** 错误摘要。 */
        String errorMessage,
        /** 重试次数。 */
        int retryCount,
        /** 重放人。 */
        String replayBy,
        /** 重放时间。 */
        Instant replayAt,
        /** 重放链路追踪 ID。 */
        String replayTraceId,
        /** 接收时间。 */
        Instant receivedAt,
        /** 处理时间。 */
        Instant processedAt
) {
}
