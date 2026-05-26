package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record AssetPage(
        /** 主键 ID。 */
        UUID id,
        /** 业务编码。 */
        String code,
        /** 名称。 */
        String name,
        /** URL 匹配规则。 */
        String urlPattern,
        /** 来源类型。 */
        String source,
        /** 外部来源引用。 */
        String sourceRef,
        /** 外部来源版本号。 */
        String sourceVersion,
        /** 页面组件树。 */
        String componentTree,
        /** 页面截图地址。 */
        String screenshotUrl,
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
