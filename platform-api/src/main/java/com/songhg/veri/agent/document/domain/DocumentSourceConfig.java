package com.songhg.veri.agent.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentSourceConfig(
        /** 主键 ID。 */
        UUID id,
        /** 来源编码。 */
        String sourceCode,
        /** 名称。 */
        String name,
        /** 来源类型。 */
        DocumentSourceType sourceType,
        /** 业务状态。 */
        DocumentSourceStatus status,
        /** 来源接入端点地址。 */
        String endpointUrl,
        /** 默认落库项目 ID。 */
        String defaultProjectId,
        /** 字段映射 ID。 */
        UUID mappingId,
        /** 密钥引用。 */
        String secretRef,
        /** 事件版本。 */
        String eventVersion,
        /** 字段映射版本。 */
        String mappingVersion,
        /** 业务说明。 */
        String description,
        /** 创建时间。 */
        Instant createdAt,
        /** 最近更新时间。 */
        Instant updatedAt
) {
}
