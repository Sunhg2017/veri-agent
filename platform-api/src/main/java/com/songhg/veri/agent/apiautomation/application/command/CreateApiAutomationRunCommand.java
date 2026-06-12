package com.songhg.veri.agent.apiautomation.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateApiAutomationRunCommand(
        @Schema(description = "已审批脚本包 ID")
        @NotNull UUID bundleId,
        @Schema(description = "可选环境 ID 或编码，仅保存引用")
        String environmentId,
        @Schema(description = "运行目标 baseUrl；后端只保存 host 和 digest")
        @NotBlank String baseUrl,
        @Schema(description = "可选用例 ID 列表；为空时运行脚本包任务下所有用例")
        List<UUID> caseIds,
        @Schema(description = "本次运行超时时间，为空时使用系统默认")
        Integer timeoutSeconds
) {
}
