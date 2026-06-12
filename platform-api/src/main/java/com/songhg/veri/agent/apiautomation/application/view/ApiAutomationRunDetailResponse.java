package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ApiAutomationRunDetailResponse(
        @Schema(description = "运行任务摘要")
        ApiAutomationRunResponse run,
        @Schema(description = "用例级运行结果摘要")
        List<ApiAutomationRunResultResponse> results
) {
}
