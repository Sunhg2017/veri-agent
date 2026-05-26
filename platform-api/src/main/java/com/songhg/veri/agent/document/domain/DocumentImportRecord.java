package com.songhg.veri.agent.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentImportRecord(
        /** 主键 ID。 */
        UUID id,
        /** 所属项目 ID。 */
        String projectId,
        /** 来源 ID。 */
        UUID sourceId,
        /** 来源编码。 */
        String sourceCode,
        /** 来源类型。 */
        DocumentSourceType sourceType,
        /** 外部来源引用。 */
        String sourceRef,
        /** 外部来源地址。 */
        String sourceUrl,
        /** 标题。 */
        String title,
        /** 业务状态。 */
        DocumentImportStatus status,
        /** 解析出的候选需求总数。 */
        int totalParsed,
        /** 已创建需求总数。 */
        int totalCreated,
        /** 已创建需求 ID 列表，按文本序列化保存。 */
        String createdRequirementIds,
        /** 错误摘要。 */
        String errorMessage,
        /** 原始输入内容摘要。 */
        String rawDigest,
        /** 创建时间。 */
        Instant createdAt,
        /** 最近更新时间。 */
        Instant updatedAt
) {
}
