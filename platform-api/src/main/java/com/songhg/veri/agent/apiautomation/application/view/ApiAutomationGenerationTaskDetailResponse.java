package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ApiAutomationGenerationTaskDetailResponse(
        @Schema(description = "生成任务摘要")
        ApiAutomationGenerationTaskResponse task,
        @Schema(description = "生成的自动化用例草稿")
        List<ApiAutomationCaseResponse> cases,
        @Schema(description = "脚本包摘要")
        List<ApiAutomationScriptBundleResponse> scriptBundles
) {
}
