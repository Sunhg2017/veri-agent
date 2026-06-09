package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Appends an operator note to a WP5 release-readiness approval work order.
 */
public record AddTestDesignReleaseReadinessNoteCommand(
        @Schema(description = "备注类型：COMMENT 或 WORK_ORDER")
        String noteType,
        @Schema(description = "备注正文，最多 1000 字；不得包含敏感正文")
        String noteText
) {
}
