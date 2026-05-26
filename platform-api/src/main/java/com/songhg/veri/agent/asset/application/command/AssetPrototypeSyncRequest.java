package com.songhg.veri.agent.asset.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AssetPrototypeSyncRequest(
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离。")
        @NotBlank String projectId,
        @Schema(description = "数据来源类型或来源系统标识。")
        @NotBlank String source,
        @Schema(description = "外部原型或集成连接器引用。")
        String connectorRef,
        @Schema(description = "来源系统版本号、节点版本或导入批次版本。")
        String sourceVersion,
        @Schema(description = "是否仅预演；true 表示不写入最终业务数据。")
        Boolean dryRun,
        @Schema(description = "页面资产列表。")
        @NotEmpty List<@Valid PageItem> pages
) {
    public record PageItem(
        @Schema(description = "名称，用于列表展示和人工识别。")
        @NotBlank String name,
        @Schema(description = "页面 URL 匹配规则。")
        String urlPattern,
        @Schema(description = "来源系统中的外部引用，用于幂等导入和回溯。")
        String sourceRef,
        @Schema(description = "来源系统版本号、节点版本或导入批次版本。")
        String sourceVersion,
        @Schema(description = "页面组件树或原型结构 JSON。")
        Object componentTree,
        @Schema(description = "页面截图地址。")
        String screenshotUrl,
        @Schema(description = "业务状态。")
        String status
) {
    }
}
