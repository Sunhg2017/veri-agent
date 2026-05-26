package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record AssetVersionHistory(
        /** 主键 ID */
        UUID id,
        /** 资产类型 */
        String assetType,
        /** 资产 ID */
        UUID assetId,
        /** 所属项目 ID */
        String projectId,
        /** 版本号 */
        int version,
        /** 变更类型 */
        String changeType,
        /** 触发变更的操作者 */
        String actor,
        /** 变更字段 */
        String changedFields,
        /** 差异 JSON */
        String diffJson,
        /** 变更后的资产完整快照 JSON */
        String snapshotJson,
        /** 链路追踪 ID */
        String traceId,
        /** 创建时间 */
        Instant createdAt
) {
}
