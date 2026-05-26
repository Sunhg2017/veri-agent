package com.songhg.veri.agent.asset.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateBusinessFlowRequest(
        @Schema(description = "名称，用于列表展示和人工识别")
        @NotBlank String name,
        @Schema(description = "业务说明或补充描述")
        String description,
        @Schema(description = "业务流程结构 JSON")
        Object flowJson,
        @Schema(description = "优先级")
        String priority,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
        @NotBlank String projectId,
        @Schema(description = "业务状态")
        String status
) {
}
