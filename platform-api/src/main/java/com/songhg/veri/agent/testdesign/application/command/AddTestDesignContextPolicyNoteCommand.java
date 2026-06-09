package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Appends an operator note to a WP5 context policy approval work order.
 */
public record AddTestDesignContextPolicyNoteCommand(
        @Schema(description = "备注类型：COMMENT 或 WORK_ORDER")
        String noteType,
        @Schema(description = "备注正文，最多 1000 字；不得包含密钥、原始上下文或 Prompt 载荷")
        String noteText
) {
}
