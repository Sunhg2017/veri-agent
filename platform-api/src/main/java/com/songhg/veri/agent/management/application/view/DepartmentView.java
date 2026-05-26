package com.songhg.veri.agent.management.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

public record DepartmentView(
        @Schema(description = "名称，用于列表展示和人工识别。")
        String name,
        @Schema(description = "上级部门或父级节点。")
        String parent,
        @Schema(description = "负责人。")
        String lead,
        @Schema(description = "成员数量或成员列表摘要。")
        int members,
        @Schema(description = "业务状态。")
        String status
) {
}
