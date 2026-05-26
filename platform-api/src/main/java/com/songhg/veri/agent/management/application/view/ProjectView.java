package com.songhg.veri.agent.management.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProjectView(
        @Schema(description = "名称，用于列表展示和人工识别。")
        String name,
        @Schema(description = "所属部门。")
        String department,
        @Schema(description = "负责人或所有者。")
        String owner,
        @Schema(description = "应用数量或应用摘要。")
        int apps,
        @Schema(description = "业务状态。")
        String status
) {
}
