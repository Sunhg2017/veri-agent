package com.songhg.veri.agent.asset.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AssetImportResponse(
        @Schema(description = "资产类型。")
        String assetType,
        @Schema(description = "导入或导出格式。")
        String format,
        @Schema(description = "是否仅预演；true 表示不写入最终业务数据。")
        boolean dryRun,
        @Schema(description = "导入文件或同步结果的总行数。")
        int totalRows,
        @Schema(description = "创建成功数量。")
        int created,
        @Schema(description = "更新成功数量。")
        int updated,
        @Schema(description = "跳过数量。")
        int skipped,
        @Schema(description = "失败数量。")
        int failed,
        @Schema(description = "逐项处理结果列表。")
        List<AssetImportItemResponse> items
) {
}
