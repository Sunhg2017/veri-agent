package com.songhg.veri.agent.asset.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

public record TestCaseStepResponse(
        @Schema(description = "步骤序号。")
        int stepOrder,
        @Schema(description = "操作类型或动作编码。")
        String action,
        @Schema(description = "预期结果。")
        String expectedResult
) {
}
