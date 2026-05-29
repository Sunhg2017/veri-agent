package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 人工处理 WP5 发布冲突的接口入参
 */
public record ResolveTestDesignConflictCommand(
        @Schema(description = "候选当前版本号，用于避免基于过期冲突结果处理")
        @NotNull(message = "候选版本号不能为空")
        Long version,
        @Schema(description = "人工确认要链接的既有 WP3 测试用例 ID")
        @NotNull(message = "测试用例 ID 不能为空")
        UUID caseId,
        @Schema(description = "处理原因")
        String reason,
        @Schema(description = "补充说明或评审意见")
        String comment
) {
}
