package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 批量人工处理 WP5 发布冲突的接口入参。
 */
public record ResolveTestDesignConflictBatchCommand(
        @Schema(description = "逐候选冲突处理目标，按提交顺序返回处理结果")
        @Valid
        @NotEmpty(message = "批量冲突处理项不能为空")
        List<@Valid @NotNull(message = "批量冲突处理项不能为空") Item> items,
        @Schema(description = "统一处理原因")
        String reason,
        @Schema(description = "统一补充说明或评审意见")
        String comment
) {
    public record Item(
            @Schema(description = "候选 ID")
            @NotNull(message = "候选 ID 不能为空")
            UUID candidateId,
            @Schema(description = "候选当前版本号，用于避免基于过期冲突结果处理")
            @NotNull(message = "候选版本号不能为空")
            Long version,
            @Schema(description = "人工确认要链接的既有 WP3 测试用例 ID")
            @NotNull(message = "测试用例 ID 不能为空")
            UUID caseId
    ) {
    }
}
