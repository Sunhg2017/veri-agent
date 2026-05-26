package com.songhg.veri.agent.asset.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateBusinessFlowRequest(
        @Schema(description = "名称，用于列表展示和人工识别。")
        @NotBlank String name,
        @Schema(description = "业务说明或补充描述。")
        String description,
        @Schema(description = "业务流程结构 JSON。")
        Object flowJson,
        @Schema(description = "优先级。")
        String priority,
        @Schema(description = "业务状态。")
        String status
) {
}
