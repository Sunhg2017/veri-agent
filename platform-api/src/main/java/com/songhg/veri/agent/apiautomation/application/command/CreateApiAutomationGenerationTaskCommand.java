package com.songhg.veri.agent.apiautomation.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateApiAutomationGenerationTaskCommand(
        @Schema(description = "所属项目 ID 或编码")
        @NotBlank String projectId,
        @Schema(description = "OpenAPI spec ID")
        @NotNull UUID specId,
        @Schema(description = "可选 WP3 API 资产 ID 列表；为空时使用已同步的全部 endpoint")
        List<UUID> assetApiIds,
        @Schema(description = "可选 WP3 测试用例 ID 列表，仅作为生成输入摘要引用")
        List<UUID> assetTestCaseIds,
        @Schema(description = "覆盖类型：SMOKE/FUNCTIONAL/EXCEPTION")
        List<String> coverageTypes,
        @Schema(description = "生成模式：FALLBACK_ONLY/MODEL_WITH_FALLBACK")
        String generationMode,
        @Schema(description = "每个 API 最多生成多少条用例")
        Integer caseCountPerApi,
        @Schema(description = "调用方幂等 key；同项目同 key 不允许指向不同 payload")
        String requestKey
) {
}
