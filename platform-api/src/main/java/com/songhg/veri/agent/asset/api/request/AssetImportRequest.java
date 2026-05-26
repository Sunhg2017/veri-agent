package com.songhg.veri.agent.asset.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AssetImportRequest(
        @Schema(description = "资产类型。")
        @NotBlank String assetType,
        @Schema(description = "导入或导出格式。")
        @NotBlank String format,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离。")
        @NotBlank String projectId,
        @Schema(description = "是否仅预演；true 表示不写入最终业务数据。")
        Boolean dryRun,
        @Schema(description = "导入文件内容，按 format 解析为目标资产。")
        @NotBlank String content
) {
}
