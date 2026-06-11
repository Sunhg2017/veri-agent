package com.songhg.veri.agent.apiautomation.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReviewApiAutomationScriptBundleCommand(
        @Schema(description = "提交评审、审批或驳回备注；驳回必填")
        String note
) {
}
