package com.songhg.veri.agent.management.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApplicationView(
        @Schema(description = "名称，用于列表展示和人工识别。")
        String name,
        @Schema(description = "类型。")
        String type,
        @Schema(description = "负责人或所有者。")
        String owner,
        @Schema(description = "乐观锁或资产版本号，用于并发控制和审计追踪。")
        String version,
        @Schema(description = "业务状态。")
        String status
) {
}
