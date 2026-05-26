package com.songhg.veri.agent.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentFieldMapping(
        /** 主键 ID。 */
        UUID id,
        /** 字段映射编码。 */
        String mappingCode,
        /** 名称。 */
        String name,
        /** 条目路径。 */
        String itemPath,
        /** 标题路径。 */
        String titlePath,
        /** 描述路径。 */
        String descriptionPath,
        /** 优先级路径。 */
        String priorityPath,
        /** 验收标准路径。 */
        String acceptanceCriteriaPath,
        /** 标签路径。 */
        String tagsPath,
        /** 创建时间。 */
        Instant createdAt,
        /** 最近更新时间。 */
        Instant updatedAt
) {
}
