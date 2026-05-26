package com.songhg.veri.agent.asset.application.view;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssetVersionHistoryResponse(
        @Schema(description = "主键 ID")
        UUID id,
        @Schema(description = "资产类型")
        String assetType,
        @Schema(description = "资产 ID")
        UUID assetId,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
        String projectId,
        @Schema(description = "乐观锁或资产版本号，用于并发控制和审计追踪")
        int version,
        @Schema(description = "变更类型")
        String changeType,
        @Schema(description = "操作人")
        String actor,
        @Schema(description = "发生变化的字段列表")
        List<String> changedFields,
        @Schema(description = "字段级差异内容")
        JsonNode diff,
        @Schema(description = "版本快照")
        JsonNode snapshot,
        @Schema(description = "链路追踪 ID")
        String traceId,
        @Schema(description = "创建时间")
        Instant createdAt
) {
}
