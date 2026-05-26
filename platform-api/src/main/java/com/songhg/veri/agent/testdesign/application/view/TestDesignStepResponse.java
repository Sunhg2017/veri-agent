package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 候选用例中的单个测试步骤出参
 */
public record TestDesignStepResponse(
        @Schema(description = "步骤序号")
        int stepOrder,
        @Schema(description = "操作类型或动作编码")
        String action,
        @Schema(description = "预期结果")
        String expectedResult
) {
}
