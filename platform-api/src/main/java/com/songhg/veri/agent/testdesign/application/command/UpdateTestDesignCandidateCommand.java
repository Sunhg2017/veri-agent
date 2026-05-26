package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

/**
 * 人工编辑 WP5 候选用例的接口入参。
 */
public record UpdateTestDesignCandidateCommand(
        @Schema(description = "标题，用于页面展示和关键字检索。")
        @NotBlank String title,
        @Schema(description = "业务说明或补充描述。")
        String description,
        @Schema(description = "关联 API 资产 ID。")
        UUID apiId,
        @Schema(description = "覆盖类型。")
        String coverageType,
        @Schema(description = "优先级。")
        String priority,
        @Schema(description = "执行前置条件。")
        String preconditions,
        @Schema(description = "测试步骤列表。")
        List<StepCommand> steps,
        @Schema(description = "预期结果。")
        String expectedResult,
        @Schema(description = "标签，多个值用列表或逗号分隔文本表达。")
        List<String> tags,
        @Schema(description = "乐观锁或资产版本号，用于并发控制和审计追踪。")
        Long version
) {
    /**
     * 人工编辑候选时提交的单个测试步骤。
 */
public record StepCommand(
        @Schema(description = "操作类型或动作编码。")
        String action,
        @Schema(description = "预期结果。")
        String expectedResult
) {
    }
}
