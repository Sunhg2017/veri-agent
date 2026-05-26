package com.songhg.veri.agent.asset.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record CreateTestCaseRequest(
        @Schema(description = "标题，用于页面展示和关键字检索。")
        @NotBlank String title,
        @Schema(description = "业务说明或补充描述。")
        String description,
        @Schema(description = "关联需求 ID。")
        UUID requirementId,
        @Schema(description = "关联 API 资产 ID。")
        UUID apiId,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离。")
        @NotBlank
        String projectId,
        @Schema(description = "业务状态。")
        String status,
        @Schema(description = "优先级。")
        String priority,
        @Schema(description = "标签，多个值用列表或逗号分隔文本表达。")
        String tags,
        @Schema(description = "测试步骤列表。")
        List<StepDto> steps
) {
    public record StepDto(
        @Schema(description = "操作类型或动作编码。")
        String action,
        @Schema(description = "预期结果。")
        String expectedResult
) {
    }
}
