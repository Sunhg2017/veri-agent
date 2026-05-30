package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Reviews a pending WP5 context policy override without storing free-form approval notes.
 */
public record ReviewTestDesignContextPolicyOverrideCommand(
        @Schema(description = "审批原因编码；仅保存枚举化编码，不保存自由文本")
        String approvalReasonCode
) {
}
