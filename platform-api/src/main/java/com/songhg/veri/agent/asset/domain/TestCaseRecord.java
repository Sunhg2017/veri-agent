package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestCaseRecord(
        /** 主键 ID。 */
        UUID id,
        /** 业务编码。 */
        String code,
        /** 标题。 */
        String title,
        /** 业务说明。 */
        String description,
        /** 所属项目 ID。 */
        String projectId,
        /** 关联需求 ID。 */
        UUID requirementId,
        /** 关联 API 资产 ID。 */
        UUID apiId,
        /** 来源类型。 */
        String source,
        /** 外部来源引用。 */
        String sourceRef,
        /** 业务状态。 */
        String status,
        /** 优先级。 */
        String priority,
        /** 标签。 */
        String tags,
        /** 测试步骤列表。 */
        List<TestCaseStep> steps,
        /** 版本号。 */
        int version,
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
) implements LifecycleManagedAsset, VersionedAsset {
    public boolean canTransitionReviewStatusTo(String nextStatus) {
        return AssetReviewStatus.canTransition(status, nextStatus);
    }

    public TestCaseRecord(
            UUID id,
            String code,
            String title,
            String description,
            String projectId,
            UUID requirementId,
            UUID apiId,
            String source,
            String sourceRef,
            String status,
            String priority,
            String tags,
            int version,
            String lifecycleStatus,
            Instant archivedAt,
            Instant deletedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id,
                code,
                title,
                description,
                projectId,
                requirementId,
                apiId,
                source,
                sourceRef,
                status,
                priority,
                tags,
                List.of(),
                version,
                lifecycleStatus,
                archivedAt,
                deletedAt,
                createdAt,
                updatedAt
        );
    }
}
