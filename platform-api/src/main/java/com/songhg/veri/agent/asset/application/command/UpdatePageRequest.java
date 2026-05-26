package com.songhg.veri.agent.asset.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdatePageRequest(
        @Schema(description = "名称，用于列表展示和人工识别")
        @NotBlank String name,
        @Schema(description = "页面 URL 匹配规则")
        String urlPattern,
        @Schema(description = "数据来源类型或来源系统标识")
        String source,
        @Schema(description = "来源系统中的外部引用，用于幂等导入和回溯")
        String sourceRef,
        @Schema(description = "来源系统版本号、节点版本或导入批次版本")
        String sourceVersion,
        @Schema(description = "页面组件树或原型结构 JSON")
        Object componentTree,
        @Schema(description = "页面截图地址")
        String screenshotUrl,
        @Schema(description = "业务状态")
        String status
) {
}
