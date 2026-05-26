package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record AssetRequirement(
        /** 主键 ID */
        UUID id,
        /** 业务编码 */
        String code,
        /** 标题 */
        String title,
        /** 业务说明 */
        String description,
        /** 来源类型 */
        String source,
        /** 外部来源引用 */
        String sourceRef,
        /** 外部来源地址 */
        String sourceUrl,
        /** 验收标准 */
        String acceptanceCriteria,
        /** 业务状态 */
        String status,
        /** 优先级 */
        String priority,
        /** 所属项目 ID */
        String projectId,
        /** 标签 */
        String tags,
        /** 版本号 */
        int version,
        /** 生命周期状态 */
        String lifecycleStatus,
        /** 归档时间 */
        Instant archivedAt,
        /** 删除时间 */
        Instant deletedAt,
        /** 创建时间 */
        Instant createdAt,
        /** 最近更新时间 */
        Instant updatedAt
) implements LifecycleManagedAsset, VersionedAsset {
    public boolean canTransitionReviewStatusTo(String nextStatus) {
        return AssetReviewStatus.canTransition(status, nextStatus);
    }
}
