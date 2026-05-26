package com.songhg.veri.agent.asset.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record PageResponse(
        @Schema(description = "主键 ID。")
        UUID id,
        @Schema(description = "业务编码，通常在同一资源范围内唯一。")
        String code,
        @Schema(description = "名称，用于列表展示和人工识别。")
        String name,
        @Schema(description = "页面 URL 匹配规则。")
        String urlPattern,
        @Schema(description = "数据来源类型或来源系统标识。")
        String source,
        @Schema(description = "来源系统中的外部引用，用于幂等导入和回溯。")
        String sourceRef,
        @Schema(description = "来源系统版本号、节点版本或导入批次版本。")
        String sourceVersion,
        @Schema(description = "页面组件树或原型结构 JSON。")
        String componentTree,
        @Schema(description = "页面截图地址。")
        String screenshotUrl,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离。")
        String projectId,
        @Schema(description = "业务状态。")
        String status,
        @Schema(description = "资产生命周期状态，例如 ACTIVE、ARCHIVED、DELETED。")
        String lifecycleStatus,
        @Schema(description = "归档时间；未归档时为空。")
        Instant archivedAt,
        @Schema(description = "删除时间；未删除时为空。")
        Instant deletedAt,
        @Schema(description = "创建时间。")
        Instant createdAt,
        @Schema(description = "最近更新时间。")
        Instant updatedAt
) {
}
