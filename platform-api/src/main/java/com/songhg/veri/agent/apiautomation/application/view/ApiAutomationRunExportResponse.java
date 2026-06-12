package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ApiAutomationRunExportResponse(
        @Schema(description = "导出格式版本")
        String schemaVersion,
        @Schema(description = "导出生成时间")
        Instant exportedAt,
        @Schema(description = "运行任务脱敏摘要")
        ApiAutomationRunResponse run,
        @Schema(description = "用例级运行结果脱敏摘要")
        List<ApiAutomationRunResultResponse> results,
        @Schema(description = "结果状态计数")
        Map<String, Integer> resultCounts,
        @Schema(description = "导出脱敏和禁出策略")
        Map<String, Object> redactionPolicy
) {
}
