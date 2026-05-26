package com.songhg.veri.agent.asset.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ApiResponseDTO(
        @Schema(description = "主键 ID。")
        UUID id,
        @Schema(description = "业务编码，通常在同一资源范围内唯一。")
        String code,
        @Schema(description = "接口或模型调用摘要。")
        String summary,
        @Schema(description = "业务说明或补充描述。")
        String description,
        @Schema(description = "HTTP 方法。")
        String httpMethod,
        @Schema(description = "接口路径。")
        String path,
        @Schema(description = "数据来源类型或来源系统标识。")
        String source,
        @Schema(description = "来源系统中的外部引用，用于幂等导入和回溯。")
        String sourceRef,
        @Schema(description = "乐观锁或资产版本号，用于并发控制和审计追踪。")
        String version,
        @Schema(description = "请求结构定义 JSON。")
        String requestSchema,
        @Schema(description = "响应结构定义 JSON。")
        String responseSchema,
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
