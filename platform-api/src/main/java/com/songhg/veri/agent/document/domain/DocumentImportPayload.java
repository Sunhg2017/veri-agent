package com.songhg.veri.agent.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentImportPayload(
        /** 导入任务 ID */
        UUID importId,
        /** 解析使用的字段映射 ID */
        UUID mappingId,
        /** 解析标题兜底值 */
        String parseFallbackTitle,
        /** 原始待解析内容；事件只携带 importId，避免 Kafka 大消息 */
        String content,
        /** 创建时间 */
        Instant createdAt,
        /** 最近更新时间 */
        Instant updatedAt
) {
}
