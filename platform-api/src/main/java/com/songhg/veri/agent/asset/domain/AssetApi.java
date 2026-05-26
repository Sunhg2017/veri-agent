package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record AssetApi(
        /** 主键 ID。 */
        UUID id,
        /** 业务编码。 */
        String code,
        /** 接口摘要。 */
        String summary,
        /** 业务说明。 */
        String description,
        /** HTTP 方法。 */
        String httpMethod,
        /** 接口路径。 */
        String path,
        /** 来源类型。 */
        String source,
        /** 外部来源引用。 */
        String sourceRef,
        /** 版本号。 */
        String version,
        /** 请求结构定义。 */
        String requestSchema,
        /** 响应结构定义。 */
        String responseSchema,
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
