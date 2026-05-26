package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record AssetBusinessFlow(
        /** 主键 ID。 */
        UUID id,
        /** 业务编码。 */
        String code,
        /** 名称。 */
        String name,
        /** 业务说明。 */
        String description,
        /** 业务流程 JSON。 */
        String flowJson,
        /** 优先级。 */
        String priority,
        /** 所属项目 ID。 */
        String projectId,
        /** 业务状态。 */
        String status,
        /** 生命周期状态。 */
        String lifecycleStatus,
        /** 归档时间。 */
        Instant archivedAt,
        /** 删除时间。 */
        Instant deletedAt,
        /** 创建时间。 */
        Instant createdAt,
        /** 最近更新时间。 */
        Instant updatedAt
) implements LifecycleManagedAsset {
}
