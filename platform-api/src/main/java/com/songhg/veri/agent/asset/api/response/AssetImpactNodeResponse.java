package com.songhg.veri.agent.asset.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record AssetImpactNodeResponse(
        @Schema(description = "资产类型")
        String assetType,
        @Schema(description = "主键 ID")
        UUID id,
        @Schema(description = "业务编码，通常在同一资源范围内唯一")
        String code,
        @Schema(description = "标题，用于页面展示和关键字检索")
        String title,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
        String projectId,
        @Schema(description = "业务状态")
        String status,
        @Schema(description = "资产生命周期状态，例如 ACTIVE、ARCHIVED、DELETED")
        String lifecycleStatus,
        @Schema(description = "最近更新时间")
        Instant updatedAt
) {
}
