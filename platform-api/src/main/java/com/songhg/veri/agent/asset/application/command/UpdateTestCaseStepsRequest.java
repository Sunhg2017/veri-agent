package com.songhg.veri.agent.asset.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateTestCaseStepsRequest(
        @Schema(description = "测试步骤列表。")
        @NotEmpty @Valid List<StepItem> steps
) {
    public record StepItem(
        @Schema(description = "操作类型或动作编码。")
        String action,
        @Schema(description = "预期结果。")
        String expectedResult
) {
    }
}
