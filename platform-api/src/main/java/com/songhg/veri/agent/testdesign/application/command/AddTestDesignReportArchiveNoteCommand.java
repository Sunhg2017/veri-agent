package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Appends an operator note to a WP5 report archive approval work order.
 */
public record AddTestDesignReportArchiveNoteCommand(
        @Schema(description = "备注类型：COMMENT 或 WORK_ORDER")
        String noteType,
        @Schema(description = "备注正文，最多 1000 字；不得包含报告正文或敏感载荷")
        String noteText
) {
}
